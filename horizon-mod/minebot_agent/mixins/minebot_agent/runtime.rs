// MineBot Agent for Horizon: the BotAgent's local HTTP API (the one MineBot's Java agent serves inside Minecraft
// Java), served from inside Horizon so every MineBot automation, the free camera / 3D view and the minimap work on
// Horizon too. This file is both the standalone Cargo library (its tests) and the module the bundle adds to the game
// as `game::minebot_agent`; the game-specific glue is `glue.rs` (`game::minebot_agent_glue`).
//
// Safety rules (MineBot's "segurança ao extremo"):
//  - nothing listens unless the game was opened by the MineBot launcher: it sets MINEBOT_AGENT (the file where the
//    port goes) and MINEBOT_AGENT_TOKEN; a game opened any other way runs exactly like plain Horizon;
//  - the server binds 127.0.0.1 only and every request needs the token (X-Token header), compared in constant time;
//  - browsers can't call it: requests carrying an Origin header are refused, and no CORS header is ever sent;
//  - bounded: 32 connections, 64 KiB requests, one game-thread budget per frame.
// Actions go through Horizon's own control commands (the same ones horizon-ctl and AI agents use), so the game keeps
// its own rules (busy tasks, screens, network checks); reads that need bulk data (world sections, entities, players,
// registries) come from the glue, on the game thread, within a time budget.

use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::net::{Ipv4Addr, TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

pub const ID: &str = "minebot_agent";
pub const NAME: &str = "MineBot Agent";
pub const VERSION: &str = "1.0.0";
pub const DESCRIPTION: &str = "Lets the MineBot launcher run its automations, free camera and minimap on Horizon (127.0.0.1 only, with a token; off unless the game was opened by MineBot).";
/// The API version MineBot reads from the port file (`api`).
pub const API: u32 = 1;

// ───────────────────────────── what the game gives ─────────────────────────────

/// A block state's facts, as the BotAgent's palette carries them.
#[derive(Clone, Debug, PartialEq)]
pub struct StateInfo {
    /// `oak_log[axis=y]` (no `minecraft:`).
    pub name: String,
    pub solid: bool,
    pub liquid: bool,
    pub air: bool,
    /// Collision height (0..1.5).
    pub height: f64,
    pub hardness: f64,
}

/// One 16x16x16 section: the state ids (x fastest, then z, then y) and, when asked, the light
/// (`sky << 4 | block`, same order).
pub enum Section {
    /// Chunk not loaded.
    Missing,
    /// Only air (no light asked).
    Air,
    Blocks { states: Vec<u32>, light: Option<Vec<u8>> },
}

#[derive(Clone, Debug, Default)]
pub struct EntityInfo {
    pub id: i64,
    /// `zombie` (no `minecraft:`).
    pub kind: String,
    pub name: Option<String>,
    pub pos: [f64; 3],
    pub yaw: f32,
    pub pitch: f32,
    pub vel: [f64; 3],
    pub width: f64,
    pub height: f64,
    pub category: String,
    pub on_ground: bool,
    pub player: bool,
    pub living: bool,
    pub health: f32,
    pub max_health: f32,
    pub baby: bool,
    pub hurt_time: i32,
    pub custom_name: Option<String>,
    /// A dropped item: `{id, count}`.
    pub item: Option<(String, u32)>,
    pub swell: Option<i32>,
}

#[derive(Clone, Debug, Default)]
pub struct PlayerInfo {
    pub name: String,
    pub uuid: String,
    pub ping: i32,
    pub game_mode: i32,
}

#[derive(Clone, Debug, Default)]
pub struct WorldInfo {
    pub min_y: i32,
    pub height: i32,
    pub in_world: bool,
}

#[derive(Clone, Debug, Default)]
pub struct Registry {
    /// (name, hardness, solid, requires tool), in block registry order.
    pub blocks: Vec<(String, f64, bool, bool)>,
    /// (name, max stack, max damage), in item registry order.
    pub items: Vec<(String, u32, u32)>,
}

/// The game, as the glue shows it. Called on the game thread only.
pub trait GameView {
    fn world(&mut self) -> WorldInfo;
    fn section(&mut self, cx: i32, sy: i32, cz: i32, light: bool) -> Section;
    fn state_info(&mut self, state: u32) -> StateInfo;
    fn entities(&mut self, radius: f64) -> Vec<EntityInfo>;
    fn players(&mut self) -> Vec<PlayerInfo>;
    fn registry(&mut self) -> Registry;
    /// Hands one control command to the game (its JSON reply comes on `reply`); an error when the game takes
    /// none (the player turned No AI on).
    fn forward(&mut self, line: String, reply: Sender<String>) -> Result<(), String>;
}

// ───────────────────────────── tiny JSON ─────────────────────────────

#[derive(Clone, Debug, PartialEq)]
pub enum J {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<J>),
    Obj(Vec<(String, J)>),
}

static NULL: J = J::Null;

impl J {
    pub fn parse(s: &str) -> Result<J, String> {
        let b = s.as_bytes();
        let mut i = 0;
        let v = parse_value(b, &mut i, 0)?;
        skip_ws(b, &mut i);
        if i != b.len() {
            return Err(format!("trailing data at {i}"));
        }
        Ok(v)
    }
    pub fn get(&self, k: &str) -> &J {
        match self {
            J::Obj(m) => m.iter().find(|(n, _)| n == k).map_or(&NULL, |(_, v)| v),
            _ => &NULL,
        }
    }
    pub fn at(&self, i: usize) -> &J {
        match self {
            J::Arr(a) => a.get(i).unwrap_or(&NULL),
            _ => &NULL,
        }
    }
    pub fn arr(&self) -> &[J] {
        match self {
            J::Arr(a) => a,
            _ => &[],
        }
    }
    pub fn f(&self) -> Option<f64> {
        match self {
            J::Num(n) => Some(*n),
            J::Str(s) => s.parse().ok(),
            _ => None,
        }
    }
    pub fn s(&self) -> Option<&str> {
        match self {
            J::Str(s) => Some(s),
            _ => None,
        }
    }
    pub fn b(&self) -> bool {
        matches!(self, J::Bool(true))
    }
    pub fn is_null(&self) -> bool {
        matches!(self, J::Null)
    }
    pub fn write(&self, out: &mut String) {
        match self {
            J::Null => out.push_str("null"),
            J::Bool(b) => out.push_str(if *b { "true" } else { "false" }),
            J::Num(n) => out.push_str(&num(*n)),
            J::Str(s) => esc(s, out),
            J::Arr(a) => {
                out.push('[');
                for (i, v) in a.iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    v.write(out);
                }
                out.push(']');
            }
            J::Obj(m) => {
                out.push('{');
                for (i, (k, v)) in m.iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    esc(k, out);
                    out.push(':');
                    v.write(out);
                }
                out.push('}');
            }
        }
    }
}

fn skip_ws(b: &[u8], i: &mut usize) {
    while *i < b.len() && matches!(b[*i], b' ' | b'\t' | b'\n' | b'\r') {
        *i += 1;
    }
}

fn parse_value(b: &[u8], i: &mut usize, depth: u32) -> Result<J, String> {
    if depth > 64 {
        return Err("too deep".into());
    }
    skip_ws(b, i);
    let Some(&c) = b.get(*i) else {
        return Err("unexpected end".into());
    };
    match c {
        b'{' => {
            *i += 1;
            let mut m = Vec::new();
            skip_ws(b, i);
            if b.get(*i) == Some(&b'}') {
                *i += 1;
                return Ok(J::Obj(m));
            }
            loop {
                skip_ws(b, i);
                let J::Str(k) = parse_value(b, i, depth + 1)? else {
                    return Err("object key".into());
                };
                skip_ws(b, i);
                if b.get(*i) != Some(&b':') {
                    return Err("expected :".into());
                }
                *i += 1;
                let v = parse_value(b, i, depth + 1)?;
                m.push((k, v));
                skip_ws(b, i);
                match b.get(*i) {
                    Some(b',') => *i += 1,
                    Some(b'}') => {
                        *i += 1;
                        return Ok(J::Obj(m));
                    }
                    _ => return Err("expected , or }".into()),
                }
            }
        }
        b'[' => {
            *i += 1;
            let mut a = Vec::new();
            skip_ws(b, i);
            if b.get(*i) == Some(&b']') {
                *i += 1;
                return Ok(J::Arr(a));
            }
            loop {
                a.push(parse_value(b, i, depth + 1)?);
                skip_ws(b, i);
                match b.get(*i) {
                    Some(b',') => *i += 1,
                    Some(b']') => {
                        *i += 1;
                        return Ok(J::Arr(a));
                    }
                    _ => return Err("expected , or ]".into()),
                }
            }
        }
        b'"' => {
            *i += 1;
            let mut s: Vec<u8> = Vec::new();
            loop {
                let Some(&c) = b.get(*i) else {
                    return Err("unterminated string".into());
                };
                *i += 1;
                match c {
                    b'"' => break,
                    b'\\' => {
                        let Some(&e) = b.get(*i) else {
                            return Err("bad escape".into());
                        };
                        *i += 1;
                        match e {
                            b'"' => s.push(b'"'),
                            b'\\' => s.push(b'\\'),
                            b'/' => s.push(b'/'),
                            b'b' => s.push(8),
                            b'f' => s.push(12),
                            b'n' => s.push(b'\n'),
                            b'r' => s.push(b'\r'),
                            b't' => s.push(b'\t'),
                            b'u' => {
                                let mut cp = hex4(b, i)?;
                                if (0xD800..0xDC00).contains(&cp) && b.get(*i) == Some(&b'\\') && b.get(*i + 1) == Some(&b'u') {
                                    *i += 2;
                                    let lo = hex4(b, i)?;
                                    cp = 0x10000 + ((cp - 0xD800) << 10) + (lo.wrapping_sub(0xDC00) & 0x3FF);
                                }
                                let ch = char::from_u32(cp).unwrap_or('\u{FFFD}');
                                let mut buf = [0u8; 4];
                                s.extend_from_slice(ch.encode_utf8(&mut buf).as_bytes());
                            }
                            _ => return Err("bad escape".into()),
                        }
                    }
                    _ => s.push(c),
                }
            }
            Ok(J::Str(String::from_utf8_lossy(&s).into_owned()))
        }
        b't' if b[*i..].starts_with(b"true") => {
            *i += 4;
            Ok(J::Bool(true))
        }
        b'f' if b[*i..].starts_with(b"false") => {
            *i += 5;
            Ok(J::Bool(false))
        }
        b'n' if b[*i..].starts_with(b"null") => {
            *i += 4;
            Ok(J::Null)
        }
        _ => {
            let st = *i;
            while *i < b.len() && matches!(b[*i], b'-' | b'+' | b'.' | b'e' | b'E' | b'0'..=b'9') {
                *i += 1;
            }
            std::str::from_utf8(&b[st..*i])
                .ok()
                .and_then(|t| t.parse::<f64>().ok())
                .map(J::Num)
                .ok_or_else(|| format!("bad value at {st}"))
        }
    }
}

fn hex4(b: &[u8], i: &mut usize) -> Result<u32, String> {
    let t = b.get(*i..*i + 4).ok_or("bad \\u")?;
    *i += 4;
    u32::from_str_radix(std::str::from_utf8(t).map_err(|_| "bad \\u")?, 16).map_err(|_| "bad \\u".into())
}

pub fn esc(s: &str, out: &mut String) {
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
}

pub fn num(n: f64) -> String {
    if !n.is_finite() {
        return "0".into();
    }
    if n.fract() == 0.0 && n.abs() < 1e15 {
        return format!("{}", n as i64);
    }
    let s = format!("{:.6}", n);
    let s = s.trim_end_matches('0').trim_end_matches('.');
    s.to_string()
}

/// A JSON object, written field by field.
pub struct Obj(String, bool);
impl Obj {
    pub fn new() -> Self {
        Obj(String::from("{"), false)
    }
    fn key(&mut self, k: &str) {
        if self.1 {
            self.0.push(',');
        }
        self.1 = true;
        esc(k, &mut self.0);
        self.0.push(':');
    }
    pub fn n(mut self, k: &str, v: f64) -> Self {
        self.key(k);
        self.0.push_str(&num(v));
        self
    }
    pub fn b(mut self, k: &str, v: bool) -> Self {
        self.key(k);
        self.0.push_str(if v { "true" } else { "false" });
        self
    }
    pub fn s(mut self, k: &str, v: &str) -> Self {
        self.key(k);
        esc(v, &mut self.0);
        self
    }
    pub fn os(self, k: &str, v: Option<&str>) -> Self {
        match v {
            Some(v) => self.s(k, v),
            None => self.raw(k, "null"),
        }
    }
    pub fn raw(mut self, k: &str, v: &str) -> Self {
        self.key(k);
        self.0.push_str(v);
        self
    }
    pub fn j(mut self, k: &str, v: &J) -> Self {
        self.key(k);
        v.write(&mut self.0);
        self
    }
    pub fn end(mut self) -> String {
        self.0.push('}');
        self.0
    }
}
impl Default for Obj {
    fn default() -> Self {
        Self::new()
    }
}

pub fn strip_ns(s: &str) -> &str {
    s.strip_prefix("minecraft:").unwrap_or(s)
}

// ───────────────────────────── BotAgent formats ─────────────────────────────

/// Sections in the BotAgent's run-length format: `{secs:[{k, r:[n,palIdx,...], l?, miss?, air?}], pal, more, minSec}`.
pub fn sections_json(view: &mut dyn GameView, list: &str, light: bool, budget: Duration, cache: &mut HashMap<u32, StateInfo>) -> String {
    let w = view.world();
    if !w.in_world {
        return r#"{"error":"not in game"}"#.into();
    }
    let t0 = Instant::now();
    let min_sec = w.min_y.div_euclid(16);
    let mut pal_idx: HashMap<u32, usize> = HashMap::new();
    let mut pal_names: HashMap<String, usize> = HashMap::new();
    let mut pal: Vec<StateInfo> = Vec::new();
    let mut secs: Vec<String> = Vec::new();
    let mut more: Vec<&str> = Vec::new();
    for it in list.split(';').filter(|s| !s.is_empty()).take(4096) {
        if t0.elapsed() > budget {
            more.push(it);
            continue;
        }
        let p: Vec<i32> = it.split(',').filter_map(|v| v.trim().parse().ok()).collect();
        if p.len() != 3 {
            secs.push(Obj::new().s("k", it).s("err", "bad key").end());
            continue;
        }
        let (cx, sy, cz) = (p[0], p[1], p[2]);
        let o = Obj::new().s("k", it);
        if sy < min_sec || sy >= min_sec + (w.height + 15) / 16 {
            secs.push(o.b("air", true).end());
            continue;
        }
        match view.section(cx, sy, cz, light) {
            Section::Missing => secs.push(o.b("miss", true).end()),
            Section::Air => secs.push(o.b("air", true).end()),
            Section::Blocks { states, light: l } => {
                let mut runs = String::from("[");
                let mut last: Option<u32> = None;
                let mut n = 0u32;
                let mut last_idx = 0usize;
                let mut first = true;
                let flush = |runs: &mut String, n: u32, idx: usize, first: &mut bool| {
                    if !*first {
                        runs.push(',');
                    }
                    *first = false;
                    runs.push_str(&n.to_string());
                    runs.push(',');
                    runs.push_str(&idx.to_string());
                };
                for &st in states.iter().take(4096) {
                    if Some(st) == last {
                        n += 1;
                        continue;
                    }
                    if n > 0 {
                        flush(&mut runs, n, last_idx, &mut first);
                    }
                    last = Some(st);
                    n = 1;
                    last_idx = match pal_idx.get(&st) {
                        Some(&i) => i,
                        None => {
                            let info = cache.entry(st).or_insert_with(|| view.state_info(st)).clone();
                            // two states with the same name share a palette entry, as in the BotAgent
                            let i = match pal_names.get(&info.name) {
                                Some(&i) => i,
                                None => {
                                    pal_names.insert(info.name.clone(), pal.len());
                                    pal.push(info);
                                    pal.len() - 1
                                }
                            };
                            pal_idx.insert(st, i);
                            i
                        }
                    };
                }
                if n > 0 {
                    flush(&mut runs, n, last_idx, &mut first);
                }
                runs.push(']');
                let mut o = o.raw("r", &runs);
                if let Some(l) = l {
                    o = o.s("l", &b64(&l));
                }
                secs.push(o.end());
            }
        }
    }
    let mut out = String::from("{\"secs\":[");
    out.push_str(&secs.join(","));
    out.push_str("],\"pal\":[");
    for (i, s) in pal.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push('[');
        esc(&s.name, &mut out);
        out.push_str(&format!(",{},{},{},{},{}]", s.solid, s.liquid, s.air, num(s.height), num(s.hardness)));
    }
    out.push_str("],\"more\":[");
    for (i, m) in more.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        esc(m, &mut out);
    }
    out.push_str(&format!("],\"minSec\":{min_sec}}}"));
    out
}

pub fn b64(data: &[u8]) -> String {
    const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for c in data.chunks(3) {
        let n = (u32::from(c[0]) << 16) | (u32::from(*c.get(1).unwrap_or(&0)) << 8) | u32::from(*c.get(2).unwrap_or(&0));
        out.push(T[(n >> 18) as usize & 63] as char);
        out.push(T[(n >> 12) as usize & 63] as char);
        out.push(if c.len() > 1 { T[(n >> 6) as usize & 63] as char } else { '=' });
        out.push(if c.len() > 2 { T[n as usize & 63] as char } else { '=' });
    }
    out
}

pub fn entities_json(list: &[EntityInfo], dists: &[f64]) -> String {
    let parts: Vec<String> = list
        .iter()
        .enumerate()
        .map(|(i, e)| {
            let d = dists.get(i).copied().unwrap_or(0.0);
            let mut o = Obj::new()
                .n("id", e.id as f64)
                .s("type", &e.kind)
                .s("name", e.name.as_deref().unwrap_or(&e.kind))
                .raw("uuid", "null")
                .n("x", e.pos[0])
                .n("y", e.pos[1])
                .n("z", e.pos[2])
                .n("yaw", f64::from(e.yaw))
                .n("pitch", f64::from(e.pitch))
                .n("vx", e.vel[0])
                .n("vy", e.vel[1])
                .n("vz", e.vel[2])
                .n("h", e.height)
                .n("w", e.width)
                .n("dist", d)
                .s("cat", &e.category.to_uppercase())
                .s("pose", "STANDING")
                .b("ground", e.on_ground)
                .b("water", false)
                .b("fire", false)
                .b("crouch", false)
                .b("sprint", false);
            if let Some(c) = &e.custom_name {
                o = o.s("cname", c);
            }
            if e.player {
                o = o.b("player", true);
            }
            if e.living {
                o = o
                    .n("hp", f64::from(e.health))
                    .n("maxHp", f64::from(e.max_health))
                    .b("baby", e.baby)
                    .b("alive", e.health > 0.0)
                    .b("using", false)
                    .n("hurt", f64::from(e.hurt_time))
                    .raw("eq", "[null,null,null,null,null,null]");
            }
            if let Some(s) = e.swell {
                o = o.n("swell", f64::from(s));
            }
            if let Some((id, count)) = &e.item {
                o = o.raw("item", &item_obj(id, f64::from(*count), None));
            }
            o.end()
        })
        .collect();
    format!("[{}]", parts.join(","))
}

/// Distances from the player, nearest first, within `radius`.
pub fn sort_entities(list: &mut Vec<EntityInfo>, me: [f64; 3], radius: f64) -> Vec<f64> {
    let d = |e: &EntityInfo| ((e.pos[0] - me[0]).powi(2) + (e.pos[1] - me[1]).powi(2) + (e.pos[2] - me[2]).powi(2)).sqrt();
    list.retain(|e| d(e) <= radius);
    list.sort_by(|a, b| d(a).total_cmp(&d(b)));
    list.iter().map(d).collect()
}

pub fn players_json(list: &[PlayerInfo]) -> String {
    const GM: [&str; 4] = ["SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"];
    let parts: Vec<String> = list
        .iter()
        .map(|p| {
            Obj::new()
                .s("name", &p.name)
                .s("uuid", &p.uuid)
                .n("ping", f64::from(p.ping.max(0)))
                .s("gm", GM.get(p.game_mode as usize).copied().unwrap_or("SURVIVAL"))
                .end()
        })
        .collect();
    format!("[{}]", parts.join(","))
}

/// Vanilla entity types for the registry: (name, category, width, height).
const ENTITY_TYPES: &[(&str, &str, f64, f64)] = &[
    ("player", "MISC", 0.6, 1.8), ("item", "MISC", 0.25, 0.25), ("experience_orb", "MISC", 0.5, 0.5), ("arrow", "MISC", 0.5, 0.5),
    ("fishing_bobber", "MISC", 0.25, 0.25), ("boat", "MISC", 1.375, 0.5625), ("minecart", "MISC", 0.98, 0.7), ("armor_stand", "MISC", 0.5, 1.975),
    ("item_frame", "MISC", 0.5, 0.5), ("painting", "MISC", 0.5, 0.5), ("tnt", "MISC", 0.98, 0.98), ("falling_block", "MISC", 0.98, 0.98),
    ("zombie", "MONSTER", 0.6, 1.95), ("husk", "MONSTER", 0.6, 1.95), ("drowned", "MONSTER", 0.6, 1.95), ("zombie_villager", "MONSTER", 0.6, 1.95),
    ("skeleton", "MONSTER", 0.6, 1.99), ("stray", "MONSTER", 0.6, 1.99), ("bogged", "MONSTER", 0.6, 1.99), ("wither_skeleton", "MONSTER", 0.7, 2.4),
    ("creeper", "MONSTER", 0.6, 1.7), ("spider", "MONSTER", 1.4, 0.9), ("cave_spider", "MONSTER", 0.7, 0.5), ("enderman", "MONSTER", 0.6, 2.9),
    ("witch", "MONSTER", 0.6, 1.95), ("slime", "MONSTER", 0.52, 0.52), ("magma_cube", "MONSTER", 0.52, 0.52), ("phantom", "MONSTER", 0.9, 0.5),
    ("pillager", "MONSTER", 0.6, 1.95), ("vindicator", "MONSTER", 0.6, 1.95), ("evoker", "MONSTER", 0.6, 1.95), ("ravager", "MONSTER", 1.95, 2.2),
    ("vex", "MONSTER", 0.4, 0.8), ("blaze", "MONSTER", 0.6, 1.8), ("ghast", "MONSTER", 4.0, 4.0), ("hoglin", "MONSTER", 1.3965, 1.4),
    ("zoglin", "MONSTER", 1.3965, 1.4), ("piglin", "MONSTER", 0.6, 1.95), ("piglin_brute", "MONSTER", 0.6, 1.95), ("zombified_piglin", "MONSTER", 0.6, 1.95),
    ("guardian", "MONSTER", 0.85, 0.85), ("elder_guardian", "MONSTER", 1.9975, 1.9975), ("silverfish", "MONSTER", 0.4, 0.3), ("endermite", "MONSTER", 0.4, 0.3),
    ("shulker", "MONSTER", 1.0, 1.0), ("warden", "MONSTER", 0.9, 2.9), ("breeze", "MONSTER", 0.6, 1.77), ("creaking", "MONSTER", 0.9, 2.7),
    ("cow", "CREATURE", 0.9, 1.4), ("pig", "CREATURE", 0.9, 0.9), ("sheep", "CREATURE", 0.9, 1.3), ("chicken", "CREATURE", 0.4, 0.7),
    ("horse", "CREATURE", 1.3965, 1.6), ("donkey", "CREATURE", 1.3965, 1.5), ("mule", "CREATURE", 1.3965, 1.6), ("llama", "CREATURE", 0.9, 1.87),
    ("wolf", "CREATURE", 0.6, 0.85), ("cat", "CREATURE", 0.6, 0.7), ("ocelot", "CREATURE", 0.6, 0.7), ("rabbit", "CREATURE", 0.4, 0.5),
    ("fox", "CREATURE", 0.6, 0.7), ("goat", "CREATURE", 0.9, 1.3), ("bee", "CREATURE", 0.7, 0.6), ("parrot", "CREATURE", 0.5, 0.9),
    ("mooshroom", "CREATURE", 0.9, 1.4), ("polar_bear", "CREATURE", 1.4, 1.4), ("panda", "CREATURE", 1.3, 1.25), ("turtle", "CREATURE", 1.2, 0.4),
    ("frog", "CREATURE", 0.5, 0.5), ("camel", "CREATURE", 1.7, 2.375), ("sniffer", "CREATURE", 1.9, 1.75), ("armadillo", "CREATURE", 0.7, 0.65),
    ("strider", "CREATURE", 0.9, 1.7), ("happy_ghast", "CREATURE", 4.0, 4.0), ("villager", "MISC", 0.6, 1.95), ("wandering_trader", "CREATURE", 0.6, 1.95),
    ("iron_golem", "MISC", 1.4, 2.7), ("snow_golem", "MISC", 0.7, 1.9), ("allay", "CREATURE", 0.35, 0.6), ("axolotl", "AXOLOTLS", 0.75, 0.42),
    ("squid", "WATER_CREATURE", 0.8, 0.8), ("glow_squid", "UNDERGROUND_WATER_CREATURE", 0.8, 0.8), ("dolphin", "WATER_CREATURE", 0.9, 0.6),
    ("cod", "WATER_AMBIENT", 0.5, 0.3), ("salmon", "WATER_AMBIENT", 0.7, 0.4), ("tropical_fish", "WATER_AMBIENT", 0.5, 0.4), ("pufferfish", "WATER_AMBIENT", 0.7, 0.7),
    ("bat", "AMBIENT", 0.5, 0.9), ("ender_dragon", "MONSTER", 16.0, 8.0), ("wither", "MONSTER", 0.9, 3.5),
];

pub fn registry_json(r: &Registry) -> String {
    let mut out = String::from("{\"blocks\":[");
    for (i, (name, hard, solid, tool)) in r.blocks.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!("[{i},"));
        esc(name, &mut out);
        out.push_str(&format!(",{},{},{}]", num(*hard), solid, tool));
    }
    out.push_str("],\"items\":[");
    for (i, (name, stack, dmg)) in r.items.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!("[{i},"));
        esc(name, &mut out);
        out.push_str(&format!(",{stack},{dmg}]"));
    }
    out.push_str("],\"entities\":[");
    for (i, (name, cat, w, h)) in ENTITY_TYPES.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!("[{i},\"{name}\",\"{cat}\",{},{}]", num(*w), num(*h)));
    }
    out.push_str("]}");
    out
}

/// Item stack sizes and durability, from the registry (the control commands only give id and count).
static ITEMS: OnceLock<Mutex<HashMap<String, (u32, u32)>>> = OnceLock::new();
fn items() -> &'static Mutex<HashMap<String, (u32, u32)>> {
    ITEMS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn pretty(id: &str) -> String {
    let mut out = String::new();
    for (i, w) in id.split('_').enumerate() {
        if i > 0 {
            out.push(' ');
        }
        let mut c = w.chars();
        if let Some(f) = c.next() {
            out.extend(f.to_uppercase());
            out.push_str(c.as_str());
        }
    }
    out
}

/// A BotAgent item: `{id, count, name, damage?, maxDamage?, stack?}`.
pub fn item_obj(id: &str, count: f64, damage: Option<f64>) -> String {
    let id = strip_ns(id);
    let known = items().lock().ok().and_then(|m| m.get(id).copied());
    let mut o = Obj::new().s("id", id).n("count", count).s("name", &pretty(id));
    if let Some((stack, max)) = known {
        if max > 0 {
            o = o.n("damage", damage.unwrap_or(0.0)).n("maxDamage", f64::from(max));
        }
        o = o.n("stack", f64::from(stack));
    } else if let Some(d) = damage {
        o = o.n("damage", d);
    }
    o.end()
}

/// A control-command stack (`{item, count, damage?}` or null) as a BotAgent item (or `null`).
pub fn item_from_ctl(v: &J) -> String {
    match v.get("item").s() {
        Some(id) if !id.is_empty() && strip_ns(id) != "air" => item_obj(id, v.get("count").f().unwrap_or(1.0), v.get("damage").f()),
        _ => "null".into(),
    }
}

/// The BotAgent screen name for a Horizon screen label.
pub fn screen_name(label: &str, state: &J) -> Option<String> {
    let n = match label {
        "" | "playing" => return None,
        "title" => {
            let m = state.get("menu");
            if !m.get("disconnected").is_null() {
                "DisconnectedScreen"
            } else if m.get("mode").s() == Some("multiplayer") {
                "JoinMultiplayerScreen"
            } else {
                "TitleScreen"
            }
        }
        "dead" => "DeathScreen",
        "inventory" => "InventoryScreen",
        "container" => "ContainerScreen",
        "station" => "ContainerScreen",
        "creative" => "CreativeModeInventoryScreen",
        "chat" => "ChatScreen",
        "sign" => "SignEditScreen",
        "book" => "BookViewScreen",
        "paused" => "PauseScreen",
        "options" => "OptionsScreen",
        "dialog" => "DialogScreen",
        other => {
            let mut s: String = other.split('-').map(pretty).collect::<Vec<_>>().join("");
            s.retain(|c| c != ' ');
            return Some(s + "Screen");
        }
    };
    Some(n.into())
}

fn text_of(v: &J) -> String {
    match v {
        J::Str(s) => s.clone(),
        J::Null => String::new(),
        J::Obj(_) => {
            let t = v.get("text");
            if let Some(s) = t.s() {
                return s.to_string();
            }
            let mut out = String::new();
            v.write(&mut out);
            out
        }
        other => {
            let mut out = String::new();
            other.write(&mut out);
            out
        }
    }
}

/// Extra facts the control `state` lacks, and caches from other replies.
#[derive(Default, Clone)]
pub struct Extra {
    pub nick: String,
    pub min_y: i32,
    pub height: i32,
    pub chat_seq: u64,
    /// The last `inventory` reply (equipment for `eq`, armor).
    pub inventory: Option<J>,
}

/// Horizon's `state` reply as the BotAgent's `/state`.
pub fn state_json(st: &J, x: &Extra, now_ms: u64) -> String {
    let label = st.get("screen").s().unwrap_or("");
    let p = st.get("player");
    let in_game = !p.is_null() && !st.get("world").is_null();
    let scr = screen_name(label, st);
    let mut o = Obj::new().os("screen", scr.as_deref());
    if label == "title" && !st.get("menu").get("disconnected").is_null() {
        o = o.s("screenTitle", "Disconnected").s("screenText", &text_of(st.get("menu").get("disconnected")));
    } else if label == "dead" {
        o = o.s("screenTitle", "You Died!").s("screenText", "");
    }
    o = o.b("inGame", in_game).b("loading", false);
    if in_game {
        let pos = p.get("position");
        let vel = p.get("velocity");
        let hp = p.get("health").f().unwrap_or(20.0);
        let (yaw, pitch) = (p.get("yaw").f().or(st.get("yaw").f()), p.get("pitch").f().or(st.get("pitch").f()));
        o = o
            .s("name", &x.nick)
            .raw("uuid", "null")
            .n("eid", -1.0)
            .n("x", pos.at(0).f().unwrap_or(0.0))
            .n("y", pos.at(1).f().unwrap_or(0.0))
            .n("z", pos.at(2).f().unwrap_or(0.0))
            .n("yaw", yaw.unwrap_or(0.0))
            .n("pitch", pitch.unwrap_or(0.0))
            .n("vx", vel.at(0).f().unwrap_or(0.0))
            .n("vy", vel.at(1).f().unwrap_or(0.0))
            .n("vz", vel.at(2).f().unwrap_or(0.0))
            .n("health", hp)
            .n("maxHealth", 20.0)
            .n("food", p.get("food").f().unwrap_or(20.0))
            .n("saturation", p.get("saturation").f().unwrap_or(5.0))
            .n("xpLevel", p.get("xp").get("level").f().unwrap_or(0.0))
            .n("xpProgress", p.get("xp").get("progress").f().unwrap_or(0.0))
            .n("armor", armor_points(x.inventory.as_ref()))
            .b("dead", hp <= 0.0 || label == "dead")
            .b("onGround", p.get("on_ground").b())
            .b("inWater", false)
            .b("inLava", false)
            .b("onFire", false)
            .b("collidedH", false)
            .b("sprinting", p.get("sprinting").b())
            .b("sneaking", p.get("sneaking").b())
            .b("swimming", false)
            .b("sleeping", !st.get("sleeping").is_null())
            .b("usingItem", !st.get("using").is_null())
            .n("air", p.get("air").f().unwrap_or(300.0))
            .n("maxAir", 300.0)
            .s("pose", if p.get("sneaking").b() { "CROUCHING" } else if p.get("fall_flying").b() { "FALL_FLYING" } else { "STANDING" })
            .n("hurtTime", p.get("hurt_time").f().unwrap_or(0.0))
            .n("attackStrength", 1.0)
            .n("slot", st.get("selected").f().unwrap_or(1.0) - 1.0)
            .raw("held", &item_from_ctl(st.get("held")))
            .raw("eq", &equipment(st, x.inventory.as_ref()));
        o = o.b("digging", !st.get("mining").is_null()).s("gameMode", st.get("gamemode").s().unwrap_or("survival"));
        let effects: Vec<String> = p
            .get("effects")
            .arr()
            .iter()
            .map(|e| {
                Obj::new()
                    .s("name", &e.get("id").s().map(|s| format!("minecraft:{}", strip_ns(s))).unwrap_or_default())
                    .n("amp", e.get("amplifier").f().unwrap_or(0.0))
                    .n("dur", e.get("duration_ticks").f().unwrap_or(0.0))
                    .end()
            })
            .collect();
        o = o.raw("effects", &format!("[{}]", effects.join(",")));
        let t = st.get("time");
        let w = st.get("weather");
        o = o
            .n("dayTime", t.get("day_time").f().unwrap_or(6000.0))
            .n("gameTime", st.get("ticks").f().unwrap_or(0.0))
            .b("raining", w.get("raining").b())
            .b("thunder", w.get("thundering").b())
            .s("dimension", st.get("dimension").s().unwrap_or("minecraft:overworld"))
            .n("minY", f64::from(x.min_y))
            .n("height", f64::from(if x.height > 0 { x.height } else { 384 }))
            .n("containerId", if matches!(label, "container" | "station") { 1.0 } else { 0.0 });
        let la = st.get("looking_at");
        match la.get("kind").s() {
            Some("block") => {
                let b = la.get("block");
                let pos = b.get("pos");
                o = o.raw(
                    "target",
                    &Obj::new()
                        .s("type", "BLOCK")
                        .n("x", pos.at(0).f().unwrap_or(0.0))
                        .n("y", pos.at(1).f().unwrap_or(0.0))
                        .n("z", pos.at(2).f().unwrap_or(0.0))
                        .s("face", &b.get("face").s().unwrap_or("UP").to_uppercase())
                        .s("block", strip_ns(b.get("state").s().or(b.get("block").s()).unwrap_or("air")))
                        .end(),
                );
            }
            Some("entity") => {
                let e = la.get("entity");
                o = o.raw(
                    "target",
                    &Obj::new()
                        .s("type", "ENTITY")
                        .n("id", e.get("id").f().unwrap_or(-1.0))
                        .s("entity", strip_ns(e.get("type").s().or(e.get("kind").s()).unwrap_or("")))
                        .end(),
                );
            }
            _ => o = o.raw("target", r#"{"type":"MISS"}"#),
        }
    }
    o.n("chatSeq", x.chat_seq as f64).n("soundSeq", 0.0).n("time", now_ms as f64).end()
}

fn armor_points(inv: Option<&J>) -> f64 {
    const PTS: &[(&str, [f64; 4])] = &[
        ("leather", [1.0, 3.0, 2.0, 1.0]),
        ("golden", [2.0, 5.0, 3.0, 1.0]),
        ("chainmail", [2.0, 5.0, 4.0, 1.0]),
        ("iron", [2.0, 6.0, 5.0, 2.0]),
        ("diamond", [3.0, 8.0, 6.0, 3.0]),
        ("netherite", [3.0, 8.0, 6.0, 3.0]),
        ("copper", [2.0, 4.0, 3.0, 1.0]),
    ];
    let Some(inv) = inv else { return 0.0 };
    let mut total = 0.0;
    for (i, a) in inv.get("armor").arr().iter().enumerate().take(4) {
        let id = strip_ns(a.get("item").s().unwrap_or(""));
        if id == "turtle_helmet" {
            total += 2.0;
            continue;
        }
        for (mat, pts) in PTS {
            if id.starts_with(mat) {
                total += pts[i];
            }
        }
    }
    total
}

/// mainhand, offhand, feet, legs, chest, head (the BotAgent's order).
fn equipment(st: &J, inv: Option<&J>) -> String {
    let held = item_from_ctl(st.get("held"));
    let Some(inv) = inv else {
        return format!("[{held},null,null,null,null,null]");
    };
    let a = inv.get("armor");
    format!(
        "[{held},{},{},{},{},{}]",
        item_from_ctl(inv.get("offhand")),
        item_from_ctl(a.at(3)),
        item_from_ctl(a.at(2)),
        item_from_ctl(a.at(1)),
        item_from_ctl(a.at(0))
    )
}

/// `inventory` as the BotAgent's `/inventory`: 41 slots (0-8 hotbar, 9-35 main, 36-39 feet..head, 40 offhand).
pub fn inventory_json(inv: &J) -> String {
    let mut slots: Vec<String> = vec!["null".into(); 41];
    for (k, s) in inv.get("hotbar").arr().iter().enumerate().take(9) {
        slots[k] = item_from_ctl(s);
    }
    for (k, s) in inv.get("main").arr().iter().enumerate().take(27) {
        slots[9 + k] = item_from_ctl(s);
    }
    let a = inv.get("armor");
    for k in 0..4 {
        slots[36 + k] = item_from_ctl(a.at(3 - k));
    }
    slots[40] = item_from_ctl(inv.get("offhand"));
    format!(
        "{{\"selected\":{},\"slots\":[{}]}}",
        num(inv.get("selected").f().unwrap_or(1.0) - 1.0),
        slots.join(",")
    )
}

/// The Java container slot order for the open Horizon screen: index = Java slot, value = Horizon slot id.
pub fn container_map(slot_ids: &[i64], station: bool) -> Vec<i64> {
    if !station {
        let mut m = vec![200, 100, 101, 102, 103, 500, 501, 502, 503];
        m.extend(9..36);
        m.extend(0..9);
        m.push(504);
        return m;
    }
    let player: Vec<i64> = (0..36).chain(500..505).chain(100..104).chain([200]).collect();
    let mut own: Vec<i64> = slot_ids.iter().copied().filter(|i| !player.contains(i)).collect();
    own.sort_unstable();
    own.dedup();
    own.extend(9..36);
    own.extend(0..9);
    own
}

/// Horizon's `screen` (or `inventory` when no screen is open) as the BotAgent's `/container`; also returns the slot map.
pub fn container_json(scr: &J, inv: &J, container_id: u32) -> (String, Vec<i64>) {
    let label = scr.get("screen").s().unwrap_or("");
    let station = matches!(label, "container" | "station");
    let mut by_id: HashMap<i64, String> = HashMap::new();
    let mut ids = Vec::new();
    for s in scr.get("slots").arr() {
        if let Some(i) = s.get("slot").f() {
            ids.push(i as i64);
            by_id.insert(i as i64, item_from_ctl(s));
        }
    }
    if by_id.is_empty() {
        // no inventory screen open: the player's inventory, as Java's inventoryMenu has it
        for (k, s) in inv.get("hotbar").arr().iter().enumerate() {
            by_id.insert(k as i64, item_from_ctl(s));
        }
        for (k, s) in inv.get("main").arr().iter().enumerate() {
            by_id.insert(9 + k as i64, item_from_ctl(s));
        }
        for (k, s) in inv.get("armor").arr().iter().enumerate() {
            by_id.insert(500 + k as i64, item_from_ctl(s));
        }
        by_id.insert(504, item_from_ctl(inv.get("offhand")));
    }
    let map = container_map(&ids, station);
    let slots: Vec<String> = map.iter().map(|i| by_id.get(i).cloned().unwrap_or_else(|| "null".into())).collect();
    let own = if station { map.len().saturating_sub(36) } else { 0 };
    let kind = if !station {
        "inventory".to_string()
    } else {
        match scr.get("station").s() {
            Some(s) => s.to_lowercase(),
            None if own % 9 == 0 && own > 0 && own <= 54 => format!("generic_9x{}", own / 9),
            None => "generic".into(),
        }
    };
    let carried = if scr.get("carried").is_null() { inv.get("carried") } else { scr.get("carried") };
    let out = Obj::new()
        .n("containerId", if station { f64::from(container_id) } else { 0.0 })
        .os("screen", screen_name(label, scr).as_deref())
        .s("type", &kind)
        .s("title", &pretty(&kind))
        .raw("carried", &item_from_ctl(carried))
        .raw("slots", &format!("[{}]", slots.join(",")))
        .end();
    (out, map)
}

/// A BotAgent inventory click as Horizon `click-slot` arguments.
pub fn click_args(map: &[i64], slot: i64, button: i64, mode: &str) -> Result<String, String> {
    let id = usize::try_from(slot).ok().and_then(|s| map.get(s)).copied().ok_or_else(|| format!("slot {slot} fora da tela aberta"))?;
    match mode.to_uppercase().as_str() {
        "PICKUP" => Ok(if button == 1 { format!("click-slot {id} right") } else { format!("click-slot {id}") }),
        "QUICK_MOVE" => Ok(format!("click-slot {id} shift")),
        other => Err(format!("clique {other} ainda não existe no Horizon")),
    }
}

// ───────────────────────────── HTTP server ─────────────────────────────

pub struct Request {
    pub method: String,
    pub path: String,
    pub q: HashMap<String, String>,
    pub token: Option<String>,
    pub origin: bool,
    pub keep_alive: bool,
}

pub fn url_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        match b[i] {
            b'+' => out.push(b' '),
            b'%' if i + 2 < b.len() => {
                match u8::from_str_radix(std::str::from_utf8(&b[i + 1..i + 3]).unwrap_or("zz"), 16) {
                    Ok(v) => {
                        out.push(v);
                        i += 2;
                    }
                    Err(_) => out.push(b'%'),
                }
            }
            c => out.push(c),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn parse_qs(s: &str, into: &mut HashMap<String, String>) {
    for kv in s.split('&').filter(|s| !s.is_empty()) {
        let (k, v) = kv.split_once('=').unwrap_or((kv, ""));
        into.insert(url_decode(k), url_decode(v));
    }
}

/// Reads one request (None on a closed or bad connection).
pub fn read_request(r: &mut impl BufRead) -> Option<Request> {
    let mut line = String::new();
    if r.read_line(&mut line).ok()? == 0 || line.len() > 8192 {
        return None;
    }
    let mut parts = line.split_whitespace();
    let method = parts.next()?.to_string();
    let target = parts.next()?.to_string();
    let http10 = parts.next().is_some_and(|v| v.ends_with("1.0"));
    let (mut len, mut token, mut origin, mut close) = (0usize, None, false, http10);
    let mut total = 0;
    loop {
        let mut h = String::new();
        if r.read_line(&mut h).ok()? == 0 {
            return None;
        }
        total += h.len();
        if total > 32768 {
            return None;
        }
        let h = h.trim_end();
        if h.is_empty() {
            break;
        }
        let (k, v) = h.split_once(':')?;
        let v = v.trim();
        match k.trim().to_ascii_lowercase().as_str() {
            "content-length" => len = v.parse().ok()?,
            "x-token" => token = Some(v.to_string()),
            "origin" => origin = true,
            "connection" => close = v.eq_ignore_ascii_case("close") || (http10 && !v.eq_ignore_ascii_case("keep-alive")),
            _ => {}
        }
    }
    if len > 65536 {
        return None;
    }
    let (path, query) = target.split_once('?').unwrap_or((&target, ""));
    let mut q = HashMap::new();
    parse_qs(query, &mut q);
    if len > 0 {
        let mut body = vec![0; len];
        r.read_exact(&mut body).ok()?;
        parse_qs(&String::from_utf8_lossy(&body), &mut q);
    }
    Some(Request { method, path: path.to_string(), q, token, origin, keep_alive: !close })
}

pub fn same(a: &str, b: &str) -> bool {
    let (a, b) = (a.as_bytes(), b.as_bytes());
    if a.len() != b.len() {
        return false;
    }
    a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

fn respond(s: &mut TcpStream, status: u16, kind: &str, body: &[u8], keep: bool) -> bool {
    let reason = match status {
        200 => "OK",
        400 => "Bad Request",
        401 => "Unauthorized",
        403 => "Forbidden",
        404 => "Not Found",
        _ => "Error",
    };
    let head = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Type: {kind}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: {}\r\n\r\n",
        body.len(),
        if keep { "keep-alive" } else { "close" }
    );
    s.write_all(head.as_bytes()).is_ok() && s.write_all(body).is_ok() && s.flush().is_ok()
}

// ───────────────────────────── the agent ─────────────────────────────

/// Work for the game thread.
pub enum Query {
    Sections { list: String, light: bool, budget: Duration },
    Entities { radius: f64, me: [f64; 3] },
    Players,
    Registry,
    World,
    Block { x: i32, y: i32, z: i32 },
}

struct Job {
    q: Query,
    reply: Sender<String>,
}

#[derive(Default)]
struct Dig {
    state: String,
    pos: [i32; 3],
    started: Option<Instant>,
    token: u64,
}

struct Agent {
    token: String,
    /// Control commands waiting for the game thread.
    out: Mutex<Vec<(String, Sender<String>)>>,
    jobs: Mutex<Vec<Job>>,
    extra: Mutex<Extra>,
    /// The last known yaw/pitch and eye position (smooth looks).
    view: Mutex<([f64; 2], [f64; 3])>,
    held: Mutex<HashMap<String, bool>>,
    chat: Mutex<(Vec<(u64, u64, String)>, u64)>,
    dig: Mutex<Dig>,
    slots: Mutex<(Vec<i64>, u32, String)>,
    frames: AtomicU64,
    conns: AtomicUsize,
    shots: AtomicU64,
}

static AGENT: OnceLock<Arc<Agent>> = OnceLock::new();
static OFF: AtomicBool = AtomicBool::new(false);
static CACHE: OnceLock<Mutex<HashMap<u32, StateInfo>>> = OnceLock::new();

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.as_millis() as u64)
}

pub fn valid_token(t: &str) -> bool {
    (16..=64).contains(&t.len()) && t.bytes().all(|c| c.is_ascii_alphanumeric())
}

fn random_token() -> String {
    use std::collections::hash_map::RandomState;
    use std::hash::{BuildHasher, Hasher};
    let mut out = String::new();
    for i in 0..4u64 {
        let mut h = RandomState::new().build_hasher();
        h.write_u64(i ^ now_ms());
        h.write_u32(std::process::id());
        out.push_str(&format!("{:016x}", h.finish()));
    }
    out
}

/// Once a frame, on the game thread (the `frame` hook).
pub fn frame(view: &mut dyn GameView) {
    if OFF.load(Ordering::Relaxed) {
        return;
    }
    let Some(agent) = AGENT.get() else {
        start();
        return;
    };
    agent.frames.fetch_add(1, Ordering::Relaxed);
    let lines: Vec<(String, Sender<String>)> = agent.out.lock().map(|mut o| std::mem::take(&mut *o)).unwrap_or_default();
    for (line, reply) in lines {
        if let Err(e) = view.forward(line, reply.clone()) {
            let _ = reply.send(Obj::new().s("error", &e).end());
        }
    }
    let jobs: Vec<Job> = match agent.jobs.lock() {
        Ok(mut j) => std::mem::take(&mut *j),
        Err(_) => return,
    };
    if jobs.is_empty() {
        return;
    }
    let cache = CACHE.get_or_init(|| Mutex::new(HashMap::new()));
    let t0 = Instant::now();
    let mut left = Vec::new();
    for job in jobs {
        // at most ~12 ms of the frame; the rest waits for the next one
        if t0.elapsed() > Duration::from_millis(12) {
            left.push(job);
            continue;
        }
        let out = run_query(view, &job.q, cache);
        let _ = job.reply.send(out);
    }
    if !left.is_empty() {
        if let Ok(mut j) = agent.jobs.lock() {
            left.append(&mut j);
            *j = left;
        }
    }
}

fn run_query(view: &mut dyn GameView, q: &Query, cache: &Mutex<HashMap<u32, StateInfo>>) -> String {
    match q {
        Query::Sections { list, light, budget } => match cache.lock() {
            Ok(mut c) => sections_json(view, list, *light, *budget, &mut c),
            Err(_) => r#"{"error":"busy"}"#.into(),
        },
        Query::Entities { radius, me } => {
            let mut l = view.entities(*radius + 2.0);
            let d = sort_entities(&mut l, *me, *radius);
            entities_json(&l, &d)
        }
        Query::Players => players_json(&view.players()),
        Query::Registry => {
            let r = view.registry();
            if let Ok(mut m) = items().lock() {
                for (n, st, d) in &r.items {
                    m.insert(n.clone(), (*st, *d));
                }
            }
            registry_json(&r)
        }
        Query::World => {
            let w = view.world();
            Obj::new().b("in", w.in_world).n("minY", f64::from(w.min_y)).n("height", f64::from(w.height)).end()
        }
        Query::Block { x, y, z } => {
            let w = view.world();
            if !w.in_world {
                return r#"{"error":"not in game"}"#.into();
            }
            match view.section(x >> 4, y >> 4, z >> 4, true) {
                Section::Blocks { states, light } => {
                    let i = (((y & 15) * 16 + (z & 15)) * 16 + (x & 15)) as usize;
                    let st = states.get(i).copied().unwrap_or(0);
                    let info = match cache.lock() {
                        Ok(mut c) => c.entry(st).or_insert_with(|| view.state_info(st)).clone(),
                        Err(_) => view.state_info(st),
                    };
                    let l = light.and_then(|l| l.get(i).copied()).unwrap_or(0xF0);
                    block_obj(*x, *y, *z, &info, l)
                }
                _ => block_obj(*x, *y, *z, &StateInfo { name: "air".into(), solid: false, liquid: false, air: true, height: 0.0, hardness: 0.0 }, 0xF0),
            }
        }
    }
}

fn block_obj(x: i32, y: i32, z: i32, i: &StateInfo, light: u8) -> String {
    Obj::new()
        .n("x", f64::from(x))
        .n("y", f64::from(y))
        .n("z", f64::from(z))
        .s("block", &i.name)
        .b("solid", i.solid)
        .b("liquid", i.liquid)
        .b("air", i.air)
        .n("h", i.height)
        .n("hard", i.hardness)
        .n("sky", f64::from(light >> 4))
        .n("light", f64::from(light & 15))
        .end()
}

fn start() {
    // Only when MineBot opened the game: it says where the port goes.
    let Some(file) = std::env::var_os("MINEBOT_AGENT") else {
        OFF.store(true, Ordering::Relaxed);
        return;
    };
    let token = std::env::var("MINEBOT_AGENT_TOKEN").ok().filter(|t| valid_token(t)).unwrap_or_else(random_token);
    let pref: u16 = std::env::var("MINEBOT_AGENT_PORT").ok().and_then(|p| p.parse().ok()).filter(|p| *p > 1024).unwrap_or(0);
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, pref)).or_else(|_| TcpListener::bind((Ipv4Addr::LOCALHOST, 0)));
    let Ok(listener) = listener else {
        OFF.store(true, Ordering::Relaxed);
        eprintln!("[minebot_agent] could not listen on 127.0.0.1");
        return;
    };
    let port = listener.local_addr().map_or(0, |a| a.port());
    let nick = std::env::var("MINEBOT_NICK").unwrap_or_default();
    let agent = Arc::new(Agent {
        token: token.clone(),
        out: Mutex::new(Vec::new()),
        jobs: Mutex::new(Vec::new()),
        extra: Mutex::new(Extra { nick, height: 384, min_y: -64, ..Default::default() }),
        view: Mutex::new(([0.0, 0.0], [0.0, 0.0, 0.0])),
        held: Mutex::new(HashMap::new()),
        chat: Mutex::new((Vec::new(), 0)),
        dig: Mutex::new(Dig { state: "idle".into(), ..Default::default() }),
        slots: Mutex::new((Vec::new(), 1, String::new())),
        frames: AtomicU64::new(0),
        conns: AtomicUsize::new(0),
        shots: AtomicU64::new(0),
    });
    if AGENT.set(agent.clone()).is_err() {
        return;
    }
    let info = Obj::new()
        .n("port", f64::from(port))
        .s("token", &token)
        .n("pid", f64::from(std::process::id()))
        .s("agent", "minebot-horizon")
        .s("version", VERSION)
        .n("api", f64::from(API))
        .n("at", now_ms() as f64)
        .end();
    let tmp = std::path::PathBuf::from(&file).with_extension("part");
    if std::fs::write(&tmp, info).and_then(|_| std::fs::rename(&tmp, &file)).is_err() {
        eprintln!("[minebot_agent] could not write the port file");
    }
    eprintln!("[minebot_agent] {VERSION} listening on 127.0.0.1:{port}");
    let a = agent.clone();
    let _ = thread::Builder::new().name("minebot-agent".into()).spawn(move || {
        for s in listener.incoming().flatten() {
            if a.conns.load(Ordering::Relaxed) >= 32 {
                continue;
            }
            a.conns.fetch_add(1, Ordering::Relaxed);
            let b = a.clone();
            let _ = thread::Builder::new().name("minebot-agent-conn".into()).spawn(move || {
                serve(&b, s);
                b.conns.fetch_sub(1, Ordering::Relaxed);
            });
        }
    });
    let a = agent;
    let _ = thread::Builder::new().name("minebot-agent-poll".into()).spawn(move || poll_loop(&a));
}

fn serve(a: &Agent, s: TcpStream) {
    let _ = s.set_nodelay(true);
    let _ = s.set_read_timeout(Some(Duration::from_secs(120)));
    let Ok(rs) = s.try_clone() else { return };
    let mut r = BufReader::new(rs);
    let mut w = s;
    while let Some(req) = read_request(&mut r) {
        let keep = req.keep_alive;
        if req.origin {
            respond(&mut w, 403, "application/json", br#"{"error":"forbidden"}"#, false);
            return;
        }
        if !req.token.as_deref().is_some_and(|t| same(t, &a.token)) {
            respond(&mut w, 401, "application/json", br#"{"error":"bad token"}"#, false);
            return;
        }
        let (status, kind, body) = match route(a, &req) {
            Ok(Reply::Json(j)) => (200, "application/json", j.into_bytes()),
            Ok(Reply::Bin(kind, b)) => (200, kind, b),
            Err(e) => (if e.starts_with("unknown ") { 404 } else { 200 }, "application/json", Obj::new().s("error", &e).end().into_bytes()),
        };
        if !respond(&mut w, status, kind, &body, keep) || !keep {
            return;
        }
        let _ = req.method;
    }
}

enum Reply {
    Json(String),
    Bin(&'static str, Vec<u8>),
}

impl Agent {
    /// One control command; its JSON, or its error.
    fn ctl(&self, line: &str, timeout: Duration) -> Result<J, String> {
        let (tx, rx): (Sender<String>, Receiver<String>) = mpsc::channel();
        if line.contains('\n') || line.contains('\r') {
            return Err("bad command".into());
        }
        if !self.push(line.to_string(), tx) {
            return Err("o jogo fechou".into());
        }
        let txt = rx.recv_timeout(timeout).map_err(|_| format!("o jogo não respondeu ({})", line.split(' ').next().unwrap_or("")))?;
        let j = J::parse(&txt).map_err(|e| format!("resposta ruim: {e}"))?;
        if let Some(e) = j.get("error").s() {
            return Err(e.to_string());
        }
        Ok(j)
    }
    /// Fire and forget (the reply is read on a short-lived thread so the game never blocks on us).
    fn send(&self, line: String) -> bool {
        let (tx, rx) = mpsc::channel::<String>();
        let ok = self.push(line, tx);
        drop(rx);
        ok
    }
    fn push(&self, line: String, reply: Sender<String>) -> bool {
        match self.out.lock() {
            Ok(mut o) if o.len() < 512 => {
                o.push((line, reply));
                true
            }
            _ => false,
        }
    }
    fn ok(&self, line: &str) -> Result<Reply, String> {
        self.ctl(line, Duration::from_secs(5)).map(|_| Reply::Json(r#"{"ok":true}"#.into()))
    }
    fn query(&self, q: Query, timeout: Duration) -> Result<String, String> {
        let (tx, rx) = mpsc::channel();
        self.jobs.lock().map_err(|_| "busy")?.push(Job { q, reply: tx });
        rx.recv_timeout(timeout).map_err(|_| "o jogo não respondeu (minimizado?)".to_string())
    }
    fn state(&self) -> Result<String, String> {
        let st = self.ctl("state", Duration::from_secs(5))?;
        let p = st.get("player");
        if !p.is_null() {
            let pos = p.get("position");
            let eye = p.get("eye");
            if let Ok(mut v) = self.view.lock() {
                v.0 = [p.get("yaw").f().unwrap_or(0.0), p.get("pitch").f().unwrap_or(0.0)];
                v.1 = [
                    eye.at(0).f().or(pos.at(0).f()).unwrap_or(0.0),
                    eye.at(1).f().or(pos.at(1).f().map(|y| y + 1.62)).unwrap_or(0.0),
                    eye.at(2).f().or(pos.at(2).f()).unwrap_or(0.0),
                ];
            }
        }
        let x = self.extra.lock().map(|x| x.clone()).unwrap_or_default();
        Ok(state_json(&st, &x, now_ms()))
    }
    fn me(&self) -> [f64; 3] {
        self.view.lock().map(|v| v.1).unwrap_or_default()
    }
}

fn qf(q: &HashMap<String, String>, k: &str, d: f64) -> f64 {
    q.get(k).and_then(|v| v.parse::<f64>().ok()).filter(|v| v.is_finite()).unwrap_or(d)
}
fn qi(q: &HashMap<String, String>, k: &str, d: i64) -> i64 {
    qf(q, k, d as f64).floor() as i64
}

fn wrap(a: f64) -> f64 {
    let mut a = a % 360.0;
    if a >= 180.0 {
        a -= 360.0;
    }
    if a < -180.0 {
        a += 360.0;
    }
    a
}

const KEYS: [(&str, &str); 9] = [
    ("forward", "key w"),
    ("back", "key s"),
    ("left", "key a"),
    ("right", "key d"),
    ("jump", "key space"),
    ("sneak", "key shift"),
    ("sprint", "key ctrl"),
    ("attack", "mouse left"),
    ("use", "mouse right"),
];

/// Commands the agent can't do on Horizon yet (reported by /selftest).
pub const MISSING: &[&str] = &["signedit", "sounds", "maps", "signs", "invclick THROW/SWAP/CLONE", "window", "fps"];

fn route(a: &Agent, req: &Request) -> Result<Reply, String> {
    let q = &req.q;
    let ok = || Ok(Reply::Json(r#"{"ok":true}"#.into()));
    let j = |s: String| Ok(Reply::Json(s));
    match req.path.as_str() {
        "/ping" => j(Obj::new().b("ready", a.frames.load(Ordering::Relaxed) > 0).s("agent", "minebot-horizon").s("version", VERSION).end()),
        "/selftest" => {
            let m: Vec<String> = MISSING.iter().map(|s| format!("\"{s}\"")).collect();
            j(format!("{{\"missing\":[{}],\"engine\":\"horizon\"}}", m.join(",")))
        }
        "/state" => j(a.state()?),
        "/tick" => {
            let st = a.state()?;
            let r = qf(q, "r", 48.0).clamp(1.0, 256.0);
            let ents = if q.contains_key("entities") { a.query(Query::Entities { radius: r, me: a.me() }, Duration::from_secs(5))? } else { "null".into() };
            let pl = if q.contains_key("players") { a.query(Query::Players, Duration::from_secs(5))? } else { "null".into() };
            j(format!("{{\"state\":{st},\"entities\":{ents},\"players\":{pl}}}"))
        }
        "/entities" => {
            let r = qf(q, "r", 48.0).clamp(1.0, 256.0);
            if a.me() == [0.0, 0.0, 0.0] {
                let _ = a.state();
            }
            j(a.query(Query::Entities { radius: r, me: a.me() }, Duration::from_secs(5))?)
        }
        "/players" => j(a.query(Query::Players, Duration::from_secs(5))?),
        "/registry" => j(a.query(Query::Registry, Duration::from_secs(30))?),
        "/sections" => {
            let budget = Duration::from_millis(qi(q, "budget", 8).clamp(1, 40) as u64);
            let list = q.get("list").cloned().unwrap_or_default();
            j(a.query(Query::Sections { list, light: q.get("light").map(String::as_str) == Some("1"), budget }, Duration::from_secs(10))?)
        }
        "/block" => j(a.query(Query::Block { x: qi(q, "x", 0) as i32, y: qi(q, "y", 0) as i32, z: qi(q, "z", 0) as i32 }, Duration::from_secs(5))?),
        "/inventory" => {
            let inv = a.ctl("inventory", Duration::from_secs(5))?;
            let out = inventory_json(&inv);
            if let Ok(mut x) = a.extra.lock() {
                x.inventory = Some(inv);
            }
            j(out)
        }
        "/container" => {
            let scr = a.ctl("screen", Duration::from_secs(5))?;
            let inv = a.ctl("inventory", Duration::from_secs(5))?;
            let sig = format!("{}|{}", scr.get("screen").s().unwrap_or(""), scr.get("slots").arr().len());
            let id = {
                let mut s = a.slots.lock().map_err(|_| "busy")?;
                if s.2 != sig {
                    s.1 = s.1 % 100 + 1;
                    s.2 = sig;
                }
                s.1
            };
            let (out, map) = container_json(&scr, &inv, id);
            if let Ok(mut s) = a.slots.lock() {
                s.0 = map;
            }
            j(out)
        }
        "/chat" => {
            let since = q.get("since").and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
            let c = a.chat.lock().map_err(|_| "busy")?;
            let parts: Vec<String> = c.0.iter().filter(|m| m.0 > since).map(|m| Obj::new().n("seq", m.0 as f64).n("time", m.1 as f64).s("text", &m.2).s("motd", &m.2).end()).collect();
            j(format!("[{}]", parts.join(",")))
        }
        "/sounds" | "/maps" | "/signs" => j("[]".into()),
        "/keys" => {
            let mut held = a.held.lock().map_err(|_| "busy")?;
            for (k, cmd) in KEYS {
                let Some(v) = q.get(k) else { continue };
                let down = v == "1" || v == "true";
                if held.get(k).copied().unwrap_or(false) == down && down {
                    continue;
                }
                held.insert(k.to_string(), down);
                a.send(format!("{cmd} {}", if down { "down" } else { "up" }));
            }
            ok()
        }
        "/release" => {
            if let Ok(mut h) = a.held.lock() {
                h.clear();
            }
            a.send("release".into());
            ok()
        }
        "/look" => {
            let (yaw, pitch, speed) = (qf(q, "yaw", 0.0), qf(q, "pitch", 0.0).clamp(-90.0, 90.0), qf(q, "speed", 0.0));
            if speed <= 0.0 {
                return a.ok(&format!("look {} {}", num(yaw), num(pitch)));
            }
            let cur = a.view.lock().map(|v| v.0).unwrap_or_default();
            let (dy, dp) = (wrap(yaw - cur[0]), pitch - cur[1]);
            let over = (dy.abs().max(dp.abs()) / speed).clamp(0.05, 3.0);
            if a.ctl(&format!("turn {} {} --over {}", num(dy), num(dp), num(over)), Duration::from_secs(1)).is_err() {
                return a.ok(&format!("look {} {}", num(yaw), num(pitch)));
            }
            ok()
        }
        "/turn" => a.ok(&format!("turn {} {}", num(qf(q, "dyaw", 0.0)), num(qf(q, "dpitch", 0.0)))),
        "/lookat" => {
            let (x, y, z) = (qf(q, "x", 0.0), qf(q, "y", 0.0), qf(q, "z", 0.0));
            let speed = qf(q, "speed", 0.0);
            let e = a.me();
            let (dx, dy, dz) = (x - e[0], y - e[1], z - e[2]);
            let yaw = (-dx).atan2(dz).to_degrees();
            let pitch = -(dy.atan2((dx * dx + dz * dz).sqrt())).to_degrees();
            if speed > 0.0 {
                let cur = a.view.lock().map(|v| v.0).unwrap_or_default();
                let (ty, tp) = (wrap(yaw - cur[0]), pitch - cur[1]);
                let over = (ty.abs().max(tp.abs()) / speed).clamp(0.05, 3.0);
                if a.ctl(&format!("turn {} {} --over {}", num(ty), num(tp), num(over)), Duration::from_secs(1)).is_ok() {
                    return ok();
                }
            }
            a.ok(&format!("look-at {} {} {}", num(x), num(y), num(z)))
        }
        "/click" => {
            let b = if q.get("button").map(String::as_str) == Some("use") { "right" } else { "left" };
            a.ok(&format!("mouse {b} click"))
        }
        "/swing" => a.ok("mouse left click"),
        "/attack" => {
            let id = qi(q, "id", -1);
            if id < 0 {
                return a.ok("mouse left click");
            }
            a.ctl(&format!("attack {id}"), Duration::from_secs(3)).map(|_| Reply::Json(r#"{"ok":true}"#.into()))
        }
        "/interact" => a.ctl(&format!("interact-entity {}", qi(q, "id", -1)), Duration::from_secs(3)).map(|_| Reply::Json(r#"{"ok":true}"#.into())),
        "/use" => {
            // the game's use-item task answers after the click lands; a long hold (eat, bow) is held with /keys use=1
            a.send("mouse right down".into());
            a.send("mouse right up".into());
            ok()
        }
        "/releaseuse" => {
            if let Ok(mut h) = a.held.lock() {
                h.insert("use".into(), false);
            }
            a.ok("mouse right up")
        }
        "/useon" => {
            let face = q.get("face").map(|f| f.to_lowercase()).unwrap_or_else(|| "up".into());
            let face = if ["down", "up", "north", "south", "west", "east"].contains(&face.as_str()) { face } else { "up".into() };
            a.ctl(&format!("interact {} {} {} {face}", qi(q, "x", 0), qi(q, "y", 0), qi(q, "z", 0)), Duration::from_secs(4)).map(|_| Reply::Json(r#"{"ok":true}"#.into()))
        }
        "/dig" => {
            let pos = [qi(q, "x", 0) as i32, qi(q, "y", 0) as i32, qi(q, "z", 0) as i32];
            let timeout = (qi(q, "timeout", 20000).clamp(500, 120000) as f64) / 1000.0;
            let tok = {
                let mut d = a.dig.lock().map_err(|_| "busy")?;
                d.token += 1;
                d.state = "digging".into();
                d.pos = pos;
                d.started = Some(Instant::now());
                d.token
            };
            let (tx, rx) = mpsc::channel::<String>();
            if !a.push(format!("break {} {} {} --timeout {}", pos[0], pos[1], pos[2], num(timeout)), tx) {
                return Err("o jogo fechou".into());
            }
            let Some(me) = AGENT.get() else {
                return Err("agent".into());
            };
            let _ = thread::Builder::new().name("minebot-agent-dig".into()).spawn(move || {
                let r = rx.recv_timeout(Duration::from_secs_f64(timeout + 6.0));
                let state = match r.ok().and_then(|t| J::parse(&t).ok()) {
                    Some(j) if j.get("error").is_null() && !j.get("ok").b().eq(&false) => "done",
                    Some(_) => "failed",
                    None => "timeout",
                };
                if let Ok(mut d) = me.dig.lock() {
                    if d.token == tok && d.state == "digging" {
                        d.state = state.into();
                    }
                }
            });
            ok()
        }
        "/digstop" => {
            if let Ok(mut d) = a.dig.lock() {
                if d.state == "digging" {
                    d.state = "aborted".into();
                }
            }
            a.send("stop".into());
            ok()
        }
        "/dig/status" => {
            let d = a.dig.lock().map_err(|_| "busy")?;
            let mut o = Obj::new().s("state", &d.state);
            if d.state == "digging" {
                o = o.n("x", f64::from(d.pos[0])).n("y", f64::from(d.pos[1])).n("z", f64::from(d.pos[2])).n("ms", d.started.map_or(0.0, |t| t.elapsed().as_millis() as f64));
            }
            j(o.end())
        }
        "/slot" => a.ok(&format!("select {}", qi(q, "n", 0).clamp(0, 8) + 1)),
        "/invclick" => {
            let map = a.slots.lock().map(|s| s.0.clone()).unwrap_or_default();
            let map = if map.is_empty() { container_map(&[], false) } else { map };
            a.ok(&click_args(&map, qi(q, "slot", 0), qi(q, "button", 0), q.get("mode").map_or("PICKUP", String::as_str))?)
        }
        "/invclicks" => {
            let map = a.slots.lock().map(|s| s.0.clone()).unwrap_or_default();
            let map = if map.is_empty() { container_map(&[], false) } else { map };
            let mut n = 0;
            for c in q.get("list").map_or("", String::as_str).split(';').filter(|s| !s.is_empty()) {
                let p: Vec<&str> = c.split(',').collect();
                let slot = p.first().and_then(|v| v.parse().ok()).unwrap_or(-1);
                let button = p.get(1).and_then(|v| v.parse().ok()).unwrap_or(0);
                a.ctl(&click_args(&map, slot, button, p.get(2).copied().unwrap_or("PICKUP"))?, Duration::from_secs(5))?;
                n += 1;
            }
            j(format!("{{\"ok\":true,\"n\":{n}}}"))
        }
        "/drop" => a.ok(if q.contains_key("all") { "drop all" } else { "drop" }),
        "/chat/send" => {
            let text: String = q.get("text").map_or("", String::as_str).chars().filter(|c| !c.is_control()).take(256).collect();
            if text.is_empty() {
                return Err("texto vazio".into());
            }
            a.ok(&format!("chat {text}"))
        }
        "/respawn" => a.ok("respawn"),
        "/closescreen" => {
            if a.ctl("close", Duration::from_secs(3)).is_err() {
                let _ = a.ctl("resume", Duration::from_secs(3));
            }
            ok()
        }
        "/connect" => {
            let addr: String = q.get("address").map_or("127.0.0.1", String::as_str).chars().filter(|c| c.is_ascii_alphanumeric() || ".:-_[]".contains(*c)).take(255).collect();
            a.ok(&format!("connect {addr}"))
        }
        "/title" | "/disconnect" => {
            let _ = a.ctl("disconnect", Duration::from_secs(5));
            ok()
        }
        "/quit" => {
            a.send("quit".into());
            ok()
        }
        "/render" => a.ok(&format!("render-distance {}", qi(q, "n", 4).clamp(2, 32))),
        "/fps" | "/window" => ok(),
        "/ungrab" => {
            a.send("release".into());
            ok()
        }
        "/frame" => {
            let n = a.shots.fetch_add(1, Ordering::Relaxed);
            let path = std::env::temp_dir().join(format!("minebot-frame-{}-{}.png", std::process::id(), n % 4));
            let _ = std::fs::remove_file(&path);
            a.ctl(&format!("screenshot {}", path.display()), Duration::from_secs(8))?;
            let t0 = Instant::now();
            while t0.elapsed() < Duration::from_secs(5) {
                if let Ok(b) = std::fs::read(&path) {
                    if b.len() > 8 && b.ends_with(&[0xAE, 0x42, 0x60, 0x82]) {
                        let _ = std::fs::remove_file(&path);
                        return Ok(Reply::Bin("image/png", b));
                    }
                }
                thread::sleep(Duration::from_millis(30));
            }
            Err("o print não ficou pronto".into())
        }
        "/signedit" => Err("editar placas ainda não existe no Horizon".into()),
        other => Err(format!("unknown {other}")),
    }
}

/// Every 300 ms: chat (from the game's events), the world's height and the inventory (for armor and equipment).
fn poll_loop(a: &Agent) {
    let mut since: u64 = 0;
    let mut n: u64 = 0;
    loop {
        thread::sleep(Duration::from_millis(300));
        n += 1;
        if let Ok(ev) = a.ctl(&format!("events --since {since}"), Duration::from_secs(3)) {
            if let Some(last) = ev.get("last").f() {
                since = last as u64;
            }
            let lines: Vec<String> = ev.get("events").arr().iter().filter(|e| e.get("event").s() == Some("chat")).filter_map(|e| e.get("text").s().map(str::to_string)).collect();
            if !lines.is_empty() {
                if let Ok(mut c) = a.chat.lock() {
                    for t in lines {
                        c.1 += 1;
                        let seq = c.1;
                        c.0.push((seq, now_ms(), t));
                    }
                    let len = c.0.len();
                    if len > 300 {
                        c.0.drain(..len - 300);
                    }
                    let seq = c.1;
                    if let Ok(mut x) = a.extra.lock() {
                        x.chat_seq = seq;
                    }
                }
            }
        }
        if n % 10 == 1 {
            if let Ok(w) = a.query(Query::World, Duration::from_secs(3)) {
                if let Ok(w) = J::parse(&w) {
                    if let Ok(mut x) = a.extra.lock() {
                        x.min_y = w.get("minY").f().unwrap_or(-64.0) as i32;
                        x.height = w.get("height").f().unwrap_or(384.0) as i32;
                    }
                }
            }
        }
        if n % 4 == 2 {
            if let Ok(inv) = a.ctl("inventory", Duration::from_secs(3)) {
                if let Ok(mut x) = a.extra.lock() {
                    x.inventory = Some(inv);
                }
            }
        }
    }
}

// ───────────────────────────── tests ─────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;

    struct Fake {
        sent: Arc<Mutex<Vec<String>>>,
    }
    impl GameView for Fake {
        fn world(&mut self) -> WorldInfo {
            WorldInfo { min_y: -64, height: 384, in_world: true }
        }
        fn section(&mut self, cx: i32, sy: i32, _cz: i32, light: bool) -> Section {
            if cx == 9 {
                return Section::Missing;
            }
            if sy > 4 {
                return Section::Air;
            }
            let mut states = vec![1u32; 4096];
            for s in states.iter_mut().skip(256) {
                *s = 0;
            }
            Section::Blocks { states, light: light.then(|| vec![0xF0; 4096]) }
        }
        fn state_info(&mut self, state: u32) -> StateInfo {
            if state == 0 {
                StateInfo { name: "air".into(), solid: false, liquid: false, air: true, height: 0.0, hardness: 0.0 }
            } else {
                StateInfo { name: "stone".into(), solid: true, liquid: false, air: false, height: 1.0, hardness: 1.5 }
            }
        }
        fn entities(&mut self, _r: f64) -> Vec<EntityInfo> {
            vec![
                EntityInfo { id: 7, kind: "zombie".into(), pos: [10.0, 64.0, 0.0], living: true, health: 20.0, max_health: 20.0, category: "monster".into(), ..Default::default() },
                EntityInfo { id: 8, kind: "cow".into(), pos: [2.0, 64.0, 0.0], living: true, health: 10.0, max_health: 10.0, category: "creature".into(), ..Default::default() },
                EntityInfo { id: 9, kind: "pig".into(), pos: [200.0, 64.0, 0.0], ..Default::default() },
            ]
        }
        fn players(&mut self) -> Vec<PlayerInfo> {
            vec![PlayerInfo { name: "Fox".into(), uuid: "u".into(), ping: 30, game_mode: 0 }]
        }
        fn registry(&mut self) -> Registry {
            Registry { blocks: vec![("air".into(), 0.0, false, false), ("stone".into(), 1.5, true, true)], items: vec![("diamond_sword".into(), 1, 1561)] }
        }
        fn forward(&mut self, line: String, reply: Sender<String>) -> Result<(), String> {
            self.sent.lock().unwrap().push(line);
            let _ = reply.send(r#"{"ok":true}"#.into());
            Ok(())
        }
    }

    #[test]
    fn json_round_trip() {
        let j = J::parse(r#"{"a":[1,2.5,-3e2,"x\"yé"],"b":null,"c":true,"d":{"e":"f"}}"#).unwrap();
        assert_eq!(j.get("a").at(1).f(), Some(2.5));
        assert_eq!(j.get("a").at(2).f(), Some(-300.0));
        assert_eq!(j.get("a").at(3).s(), Some("x\"yé"));
        assert!(j.get("b").is_null());
        assert!(j.get("c").b());
        assert_eq!(j.get("d").get("e").s(), Some("f"));
        let mut out = String::new();
        j.write(&mut out);
        assert_eq!(J::parse(&out).unwrap(), j);
        assert!(J::parse("{").is_err());
        assert!(J::parse("[1,]").is_err());
    }

    #[test]
    fn sections_run_length_and_palette() {
        let mut f = Fake { sent: Arc::default() };
        let mut cache = HashMap::new();
        let s = sections_json(&mut f, "0,0,0;9,0,0;0,10,0;0,-10,0", true, Duration::from_secs(1), &mut cache);
        let j = J::parse(&s).unwrap();
        let secs = j.get("secs").arr();
        assert_eq!(secs.len(), 4);
        assert_eq!(secs[0].get("k").s(), Some("0,0,0"));
        let r: Vec<f64> = secs[0].get("r").arr().iter().filter_map(J::f).collect();
        assert_eq!(r, vec![256.0, 0.0, 3840.0, 1.0]);
        assert_eq!(secs[0].get("l").s().unwrap().len(), 4096 / 3 * 4 + 4);
        assert!(secs[1].get("miss").b());
        assert!(secs[2].get("air").b());
        assert!(secs[3].get("air").b());
        let pal = j.get("pal").arr();
        assert_eq!(pal[0].at(0).s(), Some("stone"));
        assert!(pal[0].at(1).b());
        assert_eq!(pal[1].at(0).s(), Some("air"));
        assert_eq!(j.get("minSec").f(), Some(-4.0));
    }

    #[test]
    fn sections_respect_the_budget() {
        let mut f = Fake { sent: Arc::default() };
        let mut cache = HashMap::new();
        let s = sections_json(&mut f, "0,0,0;1,0,0", false, Duration::ZERO, &mut cache);
        let j = J::parse(&s).unwrap();
        assert_eq!(j.get("more").arr().len(), 2);
    }

    #[test]
    fn base64_matches() {
        assert_eq!(b64(b"Man"), "TWFu");
        assert_eq!(b64(b"Ma"), "TWE=");
        assert_eq!(b64(b"M"), "TQ==");
    }

    #[test]
    fn entities_nearest_first_with_distance() {
        let mut f = Fake { sent: Arc::default() };
        let cache = Mutex::new(HashMap::new());
        let s = run_query(&mut f, &Query::Entities { radius: 48.0, me: [0.0, 64.0, 0.0] }, &cache);
        let j = J::parse(&s).unwrap();
        let a = j.arr();
        assert_eq!(a.len(), 2);
        assert_eq!(a[0].get("type").s(), Some("cow"));
        assert_eq!(a[0].get("dist").f(), Some(2.0));
        assert_eq!(a[1].get("dist").f(), Some(10.0));
        assert_eq!(a[1].get("cat").s(), Some("MONSTER"));
        assert_eq!(a[1].get("hp").f(), Some(20.0));
    }

    #[test]
    fn state_translation() {
        let st = J::parse(r#"{"screen":"playing","world":"Server","dimension":"minecraft:overworld","gamemode":"survival","selected":3,
          "held":{"item":"minecraft:diamond_sword","count":1},"time":{"day_time":1000},"weather":{"raining":true},
          "player":{"position":[1.5,64,-2.5],"eye":[1.5,65.62,-2.5],"yaw":90,"pitch":10,"velocity":[0,0,0],"on_ground":true,"health":15,"food":18,"saturation":2,"xp":{"level":3,"progress":0.5},"air":300,"effects":[{"id":"minecraft:speed","duration_ticks":100,"amplifier":1}]},
          "looking_at":{"kind":"block","block":{"pos":[1,63,-3],"block":"minecraft:grass_block","state":"minecraft:grass_block[snowy=false]"},"entity":null}}"#)
        .unwrap();
        let x = Extra { nick: "Fox".into(), min_y: -64, height: 384, ..Default::default() };
        let j = J::parse(&state_json(&st, &x, 5)).unwrap();
        assert!(j.get("inGame").b());
        assert!(j.get("screen").is_null());
        assert_eq!(j.get("x").f(), Some(1.5));
        assert_eq!(j.get("health").f(), Some(15.0));
        assert_eq!(j.get("slot").f(), Some(2.0));
        assert_eq!(j.get("held").get("id").s(), Some("diamond_sword"));
        assert_eq!(j.get("target").get("block").s(), Some("grass_block[snowy=false]"));
        assert_eq!(j.get("effects").at(0).get("amp").f(), Some(1.0));
        assert!(j.get("raining").b());
        assert!(!j.get("dead").b());
        let dead = J::parse(r#"{"screen":"dead","world":"w","player":{"health":0}}"#).unwrap();
        let j = J::parse(&state_json(&dead, &x, 5)).unwrap();
        assert!(j.get("dead").b());
        assert_eq!(j.get("screen").s(), Some("DeathScreen"));
        let disc = J::parse(r#"{"screen":"title","world":null,"player":null,"menu":{"mode":"multiplayer","disconnected":"Kicked: afk"}}"#).unwrap();
        let j = J::parse(&state_json(&disc, &x, 5)).unwrap();
        assert!(!j.get("inGame").b());
        assert_eq!(j.get("screen").s(), Some("DisconnectedScreen"));
        assert_eq!(j.get("screenText").s(), Some("Kicked: afk"));
    }

    #[test]
    fn inventory_and_container_order() {
        let inv = J::parse(r#"{"selected":2,"hotbar":[{"slot":0,"item":"minecraft:stone","count":5},{"slot":1,"item":null}],
          "main":[{"slot":9,"item":"minecraft:dirt","count":1}],
          "armor":[{"slot":500,"item":"minecraft:iron_helmet","count":1},{"slot":501,"item":null},{"slot":502,"item":null},{"slot":503,"item":"minecraft:iron_boots","count":1}],
          "offhand":{"slot":504,"item":"minecraft:shield","count":1}}"#)
        .unwrap();
        let j = J::parse(&inventory_json(&inv)).unwrap();
        assert_eq!(j.get("selected").f(), Some(1.0));
        let s = j.get("slots").arr();
        assert_eq!(s.len(), 41);
        assert_eq!(s[0].get("id").s(), Some("stone"));
        assert_eq!(s[9].get("id").s(), Some("dirt"));
        assert_eq!(s[36].get("id").s(), Some("iron_boots"));
        assert_eq!(s[39].get("id").s(), Some("iron_helmet"));
        assert_eq!(s[40].get("id").s(), Some("shield"));
        assert_eq!(armor_points(Some(&inv)), 4.0);
        // the player's inventory menu, Java order
        let (c, map) = container_json(&J::parse(r#"{"screen":"playing"}"#).unwrap(), &inv, 1);
        let c = J::parse(&c).unwrap();
        assert_eq!(c.get("containerId").f(), Some(0.0));
        assert_eq!(c.get("slots").arr().len(), 46);
        assert_eq!(c.get("slots").at(36).get("id").s(), Some("stone"));
        assert_eq!(c.get("slots").at(5).get("id").s(), Some("iron_helmet"));
        assert_eq!(map[45], 504);
        // a chest: its 27 slots first, then the player's
        let mut slots = String::from("[");
        for i in 0..27 {
            slots.push_str(&format!("{{\"slot\":{},\"item\":null}},", 1000 + i));
        }
        slots.push_str(r#"{"slot":0,"item":"minecraft:stone","count":5}]"#);
        let scr = J::parse(&format!(r#"{{"screen":"container","slots":{slots}}}"#)).unwrap();
        let (c, map) = container_json(&scr, &inv, 4);
        let c = J::parse(&c).unwrap();
        assert_eq!(c.get("type").s(), Some("generic_9x3"));
        assert_eq!(c.get("containerId").f(), Some(4.0));
        assert_eq!(map.len(), 27 + 36);
        assert_eq!(map[0], 1000);
        assert_eq!(map[27], 9);
        assert_eq!(map[54], 0);
        assert_eq!(click_args(&map, 54, 0, "PICKUP").unwrap(), "click-slot 0");
        assert_eq!(click_args(&map, 3, 1, "PICKUP").unwrap(), "click-slot 1003 right");
        assert_eq!(click_args(&map, 3, 0, "QUICK_MOVE").unwrap(), "click-slot 1003 shift");
        assert!(click_args(&map, 99, 0, "PICKUP").is_err());
        assert!(click_args(&map, 1, 0, "THROW").is_err());
    }

    #[test]
    fn http_parsing_and_auth() {
        let raw = b"POST /keys?forward=1 HTTP/1.1\r\nX-Token: abcdefghijklmnop\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 18\r\n\r\njump=1&text=a+b%21GET";
        let mut r = BufReader::new(&raw[..]);
        let req = read_request(&mut r).unwrap();
        assert_eq!(req.path, "/keys");
        assert_eq!(req.q.get("forward").map(String::as_str), Some("1"));
        assert_eq!(req.q.get("text").map(String::as_str), Some("a b!"));
        assert_eq!(req.token.as_deref(), Some("abcdefghijklmnop"));
        assert!(req.keep_alive);
        assert!(!req.origin);
        assert!(same("abc", "abc"));
        assert!(!same("abc", "abd"));
        assert!(!same("abc", "abcd"));
        assert!(valid_token("abcdefghijklmnop"));
        assert!(!valid_token("short"));
        assert!(!valid_token("abcdefghijklmnop!"));
        assert_eq!(random_token().len(), 64);
        let raw = b"GET /state HTTP/1.1\r\nOrigin: http://evil\r\n\r\n";
        assert!(read_request(&mut BufReader::new(&raw[..])).unwrap().origin);
    }

    #[test]
    fn registry_and_items() {
        let mut f = Fake { sent: Arc::default() };
        let cache = Mutex::new(HashMap::new());
        let r = J::parse(&run_query(&mut f, &Query::Registry, &cache)).unwrap();
        assert_eq!(r.get("blocks").at(1).at(1).s(), Some("stone"));
        assert_eq!(r.get("items").at(0).at(2).f(), Some(1.0));
        assert!(r.get("entities").arr().iter().any(|e| e.at(1).s() == Some("zombie") && e.at(2).s() == Some("MONSTER")));
        let it = J::parse(&item_obj("minecraft:diamond_sword", 1.0, Some(5.0))).unwrap();
        assert_eq!(it.get("maxDamage").f(), Some(1561.0));
        assert_eq!(it.get("name").s(), Some("Diamond Sword"));
    }

    #[test]
    fn screen_names() {
        let n = J::Null;
        assert_eq!(screen_name("gamemode-switcher", &n).as_deref(), Some("GamemodeSwitcherScreen"));
        assert_eq!(screen_name("chat", &n).as_deref(), Some("ChatScreen"));
        assert_eq!(screen_name("playing", &n), None);
    }

    #[test]
    fn served_over_http_end_to_end() {
        // the HTTP side, with a fake game answering on another thread
        let sent: Arc<Mutex<Vec<String>>> = Arc::default();
        let mut f = Fake { sent: sent.clone() };
        let agent = Arc::new(Agent {
            token: "abcdefghijklmnop".into(),
            out: Mutex::new(Vec::new()),
            jobs: Mutex::new(Vec::new()),
            extra: Mutex::new(Extra::default()),
            view: Mutex::new(([0.0, 0.0], [0.0, 64.0, 0.0])),
            held: Mutex::new(HashMap::new()),
            chat: Mutex::new((vec![(1, 1, "hi".into())], 1)),
            dig: Mutex::new(Dig::default()),
            slots: Mutex::new((Vec::new(), 1, String::new())),
            frames: AtomicU64::new(1),
            conns: AtomicUsize::new(0),
            shots: AtomicU64::new(0),
        });
        let l = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let port = l.local_addr().unwrap().port();
        let a2 = agent.clone();
        thread::spawn(move || {
            for s in l.incoming().flatten() {
                let b = a2.clone();
                thread::spawn(move || serve(&b, s));
            }
        });
        let a3 = agent.clone();
        let stop = Arc::new(AtomicBool::new(false));
        let stop2 = stop.clone();
        thread::spawn(move || {
            let cache = Mutex::new(HashMap::new());
            while !stop2.load(Ordering::Relaxed) {
                let lines: Vec<(String, Sender<String>)> = std::mem::take(&mut *a3.out.lock().unwrap());
                for (l, r) in lines {
                    let _ = f.forward(l, r);
                }
                let jobs: Vec<Job> = std::mem::take(&mut *a3.jobs.lock().unwrap());
                for j in jobs {
                    let _ = j.reply.send(run_query(&mut f, &j.q, &cache));
                }
                thread::sleep(Duration::from_millis(5));
            }
        });
        let get = |path: &str, token: &str| -> String {
            let mut s = TcpStream::connect(("127.0.0.1", port)).unwrap();
            write!(s, "GET {path} HTTP/1.1\r\nX-Token: {token}\r\nConnection: close\r\n\r\n").unwrap();
            let mut out = String::new();
            s.read_to_string(&mut out).unwrap();
            out
        };
        assert!(get("/ping", "wrongwrongwrong1").starts_with("HTTP/1.1 401"));
        let r = get("/ping", "abcdefghijklmnop");
        assert!(r.contains("\"ready\":true"));
        let r = get("/sections?list=0,0,0&budget=20", "abcdefghijklmnop");
        assert!(r.contains("\"r\":[256,0,3840,1]"), "{r}");
        let r = get("/chat?since=0", "abcdefghijklmnop");
        assert!(r.contains("\"text\":\"hi\""));
        let r = get("/keys?forward=1&jump=0", "abcdefghijklmnop");
        assert!(r.contains("\"ok\":true"));
        thread::sleep(Duration::from_millis(50));
        let s = sent.lock().unwrap().clone();
        assert!(s.contains(&"key w down".to_string()), "{s:?}");
        assert!(s.contains(&"key space up".to_string()));
        let r = get("/nope", "abcdefghijklmnop");
        assert!(r.starts_with("HTTP/1.1 404"));
        stop.store(true, Ordering::Relaxed);
    }
}
