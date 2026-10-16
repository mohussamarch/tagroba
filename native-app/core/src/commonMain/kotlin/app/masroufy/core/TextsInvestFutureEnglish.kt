package app.masroufy.core

import app.masroufy.core.InvestFutureKey.*

/** الإنجليزي لـ«هتبقى معاك كام» (كتابة Claude — مستني مراجعة المالك §40). الفصحى والمصري في `TextsInvestFuture.kt`. */
private const val RIYAL_ONE_EN = "⁦⃁ 1⁩"

internal val ENGLISH_INVEST_FUTURE_TEXTS: Map<TextRef, String> = mapOf(
    FUT_YOU_IN to "You, in", FUT_YEARS_ONE to "1 year", FUT_YEARS_TWO to "2 years", FUT_YEARS_FEW to "{0} years", FUT_YEARS_MANY to "{0} years",
    FUT_WHEEL_A11Y to "Change the period, now {0}", FUT_YEAR_LINE to "Year {0}", FUT_AGE_SUFFIX to " · you'll be {0}",
    FUT_THEN_SUFFIX to " — in that year's money", FUT_TODAY_ROW to "Today ≈ {0}",
    FUT_NA_BODY to "Not available: the averages file hasn't arrived yet, so we can't work out growth.",
    FUT_AGE_LINK to "Enter your age in the retirement calculator →", FUT_UNKNOWN_COUNT to "{0} of your items have no known value and aren't counted",
    FUT_PATHS_TITLE to "Choose your path", FUT_PATHS_SUB to "The number above and the bold line follow your choice",
    FUT_KEEP_TITLE to "As you are", FUT_KEEP_DESC to "You save {0} a month and leave it in the bank",
    FUT_KEEP_DESC_LOW to "You spend {0} a month more than you earn", FUT_KEEP_DESC_UNKNOWN to "Your saving isn't known yet (needs 3 full months)",
    FUT_KEEP_SENTENCE to "If you carry on as you are",
    FUT_INVEST_TITLE to "Invest your savings", FUT_INVEST_DESC to "The same {0}, but invested", FUT_INVEST_SENTENCE to "If you invest your savings ({0} a month)",
    FUT_MORE_TITLE to "Invest more", FUT_MORE_TITLE_LOW to "Start saving", FUT_MORE_DESC to "You invest {0} a month",
    FUT_MORE_SENTENCE to "If you invest {0} a month", FUT_MORE_SENTENCE_LOW to "If you start saving and investing {0} a month",
    FUT_STEP_TITLE to "How much will you invest a month?", FUT_STEP_PLUS to "Add {0}", FUT_STEP_MINUS to "Reduce by {0}",
    FUT_HINT_LOW_INCOME to "We started at about 10% of your income (pay yourself first)",
    FUT_HINT_NO_INCOME to "Your income isn't known, so we started at {0}",
    FUT_HINT_NORMAL to "Your current saving {0} + about 10% of your income", FUT_HINT_NORMAL_NO_INCOME to "Your current saving {0} + {1}",
    FUT_MIX_TITLE to "Invested in", FUT_MIX_MINE to "Like your holdings", FUT_MIX_SHARES to "Shares", FUT_MIX_GOLD to "Gold", FUT_MIX_PROPERTY to "Property",
    FUT_MIX_SRC_MINE to "At an average of {0} a year (your holdings' average, weighted by value)", FUT_MIX_SRC to "At an average of {0} a year — {1}",
    FUT_MIX_SRC_MINE_SHARES to "You have no growing holdings yet, so we use the stock average of {0} a year — {1}",
    FUT_CHART_TITLE to "How much you'll have", FUT_MODE_A11Y to "Number format", FUT_MODE_TODAY to "In today's money",
    FUT_MODE_THEN to "In that year's money", FUT_CHART_NOTE to "The bold line is your path · slide your finger across the chart to see any year",
    FUT_PINS_TITLE to "Your marks", FUT_AXIS_MILLION to "{0}M", FUT_AXIS_THOUSAND to "{0}K", FUT_TIP_AGE to " · age {0}",
    FUT_CHART_A11Y_ITEM to "{0} {1} in {2}",
    FUT_WHEEL_TITLE to "In how many years?", FUT_WHEEL_HINT to "Swipe up or down", FUT_WHEEL_CLOSE to "Close", FUT_WHEEL_TYPE to "Or type the year itself",
    FUT_WHEEL_MARK to "Save as a mark", FUT_WHEEL_ERR to "Enter a year from {0} to {1}", FUT_WHEEL_REMOVE to "Remove mark {0}", FUT_WHEEL_DONE to "Done",
    FUT_PASSIVE_TITLE to "What your investments could pay you each month without working", FUT_PASSIVE_TODAY to "Today",
    FUT_PASSIVE_ON_PATH to "On the “{0}” path",
    FUT_PASSIVE_NOTE to "Only the growth above inflation, without touching the capital: gold {0} · shares {1} a year",
    FUT_PASSIVE_RENT to " + rent and certificate returns {0}",
    FUT_MILES_TITLE to "Your milestones", FUT_MILE_100K to "100K", FUT_MILE_HALF_M to "Half a million", FUT_MILE_FIRST_M to "First million",
    FUT_MILE_5M to "5 million", FUT_MILE_10M to "10 million", FUT_MILE_PASSED to "Passed", FUT_MILE_REACHED to "{0} (in {1})",
    FUT_MILE_NEVER to "Not within 40 years", FUT_GOAL_LABEL to "Goal “{0}”", FUT_GOAL_NO_DATE to "No date",
    FUT_MILES_NOTE to "Milestones on the “{0}” path · diamonds are your savings-plan goals",
    FUT_COUNTRIES_TITLE to "Both countries in {0}", FUT_COUNTRIES_TOTAL to "Approximate total",
    FUT_FX_LINE to "If everything stays as it is · at today's rate: $RIYAL_ONE_EN = {0} EGP — from the dollar price in riyals and in pounds ({1}, {2})",
    FUT_FX_WARN to "The combined figure uses today's rate and the pound moves a lot — very approximate.",
    FUT_FX_MISSING to "The pound's exchange rate isn't in the prices file yet", FUT_COUNTRY_SA to "Saudi Arabia", FUT_COUNTRY_EG to "Egypt",
)
