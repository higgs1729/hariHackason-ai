package com.hanamizuki.backend.integration.ai;

import java.util.List;

/**
 * What Claude is asked to produce for the camera screen.
 *
 * <p>A record rather than a hand-written JSON schema: the schema is derived
 * from this declaration, so the two cannot drift apart.
 *
 * @param hint  one sentence telling the group what to do
 * @param poses two or three short pose ideas
 */
public record ShootHint(String hint, List<String> poses) {
}
