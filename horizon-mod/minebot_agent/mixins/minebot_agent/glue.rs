// The game side of MineBot Agent: what `game::minebot_agent` reads from Horizon, on the game thread. Added to the
// game as `game::minebot_agent_glue` (a child of `game`, so it reads the game's own state the way Horizon's control
// commands do: `agent.rs`, `control.rs`). Only this file names Horizon's types.

use super::Game;
use super::minebot_agent as m;
use std::sync::mpsc::Sender;

pub(super) struct View<'a, 'g>(pub(super) &'a mut Game<'g>);

fn strip(s: &str) -> String {
    s.strip_prefix("minecraft:").unwrap_or(s).to_string()
}

impl m::GameView for View<'_, '_> {
    fn world(&mut self) -> m::WorldInfo {
        let g = &*self.0;
        let in_world = g.title.is_none() && g.player.is_some();
        let (bottom, top) = (g.world.bottom_y(), g.world.top_y());
        m::WorldInfo { min_y: bottom, height: (top - bottom).max(16), in_world }
    }

    fn section(&mut self, cx: i32, sy: i32, cz: i32, light: bool) -> m::Section {
        let g = &*self.0;
        let (bx, by, bz) = (cx << 4, sy << 4, cz << 4);
        if !g.world.loaded(bx, bz) {
            return m::Section::Missing;
        }
        let air = g.world.air;
        if !light && !g.world.may_hold([bx, by, bz], [bx + 15, by + 15, bz + 15], |s| s != air) {
            return m::Section::Air;
        }
        let mut states = Vec::with_capacity(4096);
        let mut lights = if light { Some(Vec::with_capacity(4096)) } else { None };
        for y in by..by + 16 {
            for z in bz..bz + 16 {
                for x in bx..bx + 16 {
                    states.push(g.world.block(x, y, z).0);
                    if let Some(l) = lights.as_mut() {
                        l.push(g.world.light(x, y, z));
                    }
                }
            }
        }
        m::Section::Blocks { states, light: lights }
    }

    fn state_info(&mut self, state: u32) -> m::StateInfo {
        let g = &*self.0;
        let id = horizon_world::registry::BlockStateId(state);
        let f = g.res.facts().get(id);
        let air = g.res.facts().is_air(id);
        let liquid = format!("{:?}", f.fluid) != "None";
        let solid = !air && (f.full_collision || f.legacy_solid);
        let name = strip(&g.state_string(id));
        // collision height without reading shapes (works on every Horizon version): the usual partial blocks by name
        let height = if !solid {
            0.0
        } else if f.full_collision {
            1.0
        } else if name.contains("fence") || name.contains("_wall") {
            1.5
        } else if name.contains("slab") && name.contains("type=bottom") {
            0.5
        } else if name.contains("carpet") {
            0.0625
        } else if name.contains("trapdoor") || name.contains("bed") || name.contains("daylight_detector") {
            0.5625
        } else {
            1.0
        };
        m::StateInfo {
            name,
            solid,
            liquid,
            air,
            height,
            hardness: f64::from(f.hardness),
        }
    }

    fn entities(&mut self, radius: f64) -> Vec<m::EntityInfo> {
        let names: std::collections::HashMap<u32, String> = self.0.remote_player_names().into_iter().collect();
        let g = &*self.0;
        let Some(p) = &g.player else {
            return Vec::new();
        };
        let me = [p.position.x, p.position.y, p.position.z];
        let near = |x: f64, y: f64, z: f64| ((x - me[0]).powi(2) + (y - me[1]).powi(2) + (z - me[2]).powi(2)).sqrt() <= radius;
        let mut out = Vec::new();
        for mob in g.mobs.iter().filter(|mob| !mob.removed) {
            if !near(mob.position.x, mob.position.y, mob.position.z) {
                continue;
            }
            let kind = strip(&mob.kind.id);
            let player = kind == "player";
            out.push(m::EntityInfo {
                id: i64::from(mob.id),
                name: if player { names.get(&mob.id).cloned() } else { None },
                pos: [mob.position.x, mob.position.y, mob.position.z],
                yaw: mob.head_yaw,
                pitch: mob.head_pitch,
                vel: [mob.velocity.x, mob.velocity.y, mob.velocity.z],
                width: mob.kind.width,
                height: mob.kind.height,
                category: mob.kind.category.clone(),
                on_ground: mob.on_ground,
                player,
                living: true,
                health: mob.health,
                max_health: mob.kind.max_health,
                baby: mob.is_baby(),
                hurt_time: mob.hurt_time,
                custom_name: mob.custom_name.clone(),
                item: None,
                swell: (kind == "creeper").then_some(mob.swell),
                kind,
            });
        }
        for item in &g.dropped {
            if !near(item.position.x, item.position.y, item.position.z) {
                continue;
            }
            out.push(m::EntityInfo {
                id: i64::from(item.id),
                kind: "item".into(),
                pos: [item.position.x, item.position.y, item.position.z],
                width: 0.25,
                height: 0.25,
                category: "misc".into(),
                item: Some((strip(&item.stack.item), item.stack.count)),
                ..Default::default()
            });
        }
        out
    }

    fn players(&mut self) -> Vec<m::PlayerInfo> {
        match self.0.remote_tab_players() {
            Some((list, _, _)) => list
                .into_iter()
                .map(|t| m::PlayerInfo { name: t.name, uuid: format!("{:032x}", t.uuid), ping: t.latency, game_mode: t.game_mode })
                .collect(),
            None => {
                let nick = std::env::var("MINEBOT_NICK").unwrap_or_else(|_| "Player".into());
                vec![m::PlayerInfo { name: nick, uuid: String::new(), ping: 0, game_mode: 0 }]
            }
        }
    }

    fn registry(&mut self) -> m::Registry {
        let g = &*self.0;
        let mut r = m::Registry::default();
        let reg = &g.pack.assets.registry;
        let mut last = String::new();
        for i in 0..reg.len() {
            let id = horizon_world::registry::BlockStateId(i as u32);
            let Some(s) = reg.state(id) else { continue };
            if s.name == last {
                continue;
            }
            last = s.name.clone();
            let f = g.res.facts().get(id);
            let solid = f.full_collision || f.legacy_solid;
            r.blocks.push((strip(&s.name), f64::from(f.hardness), solid, f.requires_tool));
        }
        let items = match &g.world_tables {
            Some(t) => &t.items,
            None => g.res.items(),
        };
        for name in &items.registry_order {
            let (stack, damage) = items.get(name).map_or((64, 0), |i| (i.max_stack, i.max_damage));
            r.items.push((strip(name), stack, damage));
        }
        r
    }

    fn forward(&mut self, line: String, reply: Sender<String>) -> Result<(), String> {
        let g = &mut *self.0;
        // The player's No AI switch wins: no command reaches the game.
        if g.settings.extras.no_ai {
            return Err("o \"No AI\" está ligado no Horizon (Configurações > AI Agent): desligue para usar as automações do MineBot".into());
        }
        if g.control.is_none() {
            // In-process commands only (no socket): the same queue `--benchmark` uses.
            g.control = Some(horizon_client::control::Server::local());
        }
        let Some(c) = g.control.as_ref() else {
            return Err("sem comandos".into());
        };
        c.sender()
            .send(horizon_client::control::Request { line, reply })
            .map_err(|_| "o jogo fechou".to_string())
    }
}
