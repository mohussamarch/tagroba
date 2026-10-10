package app.masroufy.usecase

/**
 * كل حالات استخدام المساعد «مصروفي» لبلد واحدة من نفس الاعتمادات (OVERRIDES §78 · §79.2 · §79.3) — شاشة المساعد بتنادي دول بس:
 * المحادثة · «اللي اتعلمته عنك» · السجل · الأسئلة اللي ما اتفهمتش · «×» على كروت «أمور لم تُنجزها بعد».
 */
class AssistantSuite(val deps: AssistantDeps) {
    val chat = AssistantChat(deps)
    val memory = AssistantMemory(deps)
    val history = AssistantHistory(deps)
    val unknown = AssistantUnknownLog(deps)
    val startCards = ManageStartCards(deps.stores.forgotten, deps.clock)
}
