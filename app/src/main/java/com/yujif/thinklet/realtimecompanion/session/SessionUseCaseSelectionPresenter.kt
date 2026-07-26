package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase

data class SessionUseCaseSelectionPresentation(
    val statusText: String,
    val speech: String,
    val utteranceId: String,
)

object SessionUseCaseSelectionPresenter {
    fun presentSelected(useCase: RealtimeCompanionUseCase): SessionUseCaseSelectionPresentation {
        val speech = "${useCase.japaneseSpeechName}に切り替えました"
        return SessionUseCaseSelectionPresentation(
            statusText = speech,
            speech = speech,
            utteranceId = "use_case_${useCase.id}",
        )
    }
}
