package com.hanamizuki.backend.integration.ai;

import java.util.List;

/**
 * What Claude is asked to produce for one cluster of photos.
 *
 * <p>The Java SDK derives the JSON schema from this record, so the field names
 * and types here are the prompt's output contract — renaming one changes what
 * the model is asked for.
 *
 * @param title        short, Japanese, e.g. 「最高の1日」
 * @param coverPhotoId must be one of the ids that went in; validated, not trusted
 * @param summary      one sentence for the album card
 */
public record AlbumDraft(String title, Long coverPhotoId, String summary,
                         List<PhotoInsight> photos) {
}
