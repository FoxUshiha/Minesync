// Once a frame: MineBot Agent answers what waits for the game thread (control commands, world reads).
fn minebot_agent_frame(game: &mut crate::game::Game<'_>) {
    crate::game::minebot_agent::frame(&mut crate::game::minebot_agent_glue::View(game));
}
