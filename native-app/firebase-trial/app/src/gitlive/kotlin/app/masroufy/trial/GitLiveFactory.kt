package app.masroufy.trial

import android.content.Context

/** نسخة GitLive: القارئ نفسه في الكود المشترك (`:gitlive`)، هنا بس بنديله `Context` بتاع أندرويد. */
fun createReader(context: Context): TrialReader = GitLiveReader(context)
