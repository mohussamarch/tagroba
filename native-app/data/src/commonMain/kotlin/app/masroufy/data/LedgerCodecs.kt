package app.masroufy.data

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonAllocation
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag

/** العمليات والديون والاستيراد — الحقول بنفس أسماء التطبيق الحالي (`src/domain/entities/types.ts`). */
object LedgerCodecs {
    val transactions: DocCodec<Transaction> = codec(
        "transactions", { it.id },
        { t ->
            doc {
                req("id", t.id); req("occurredAt", t.occurredAt); req("datePrecision", t.datePrecision); opt("sourceTime", t.sourceTime)
                req("sourceOrder", t.sourceOrder); req("economicKind", t.economicKind.wire); req("economicKindConfirmed", t.economicKindConfirmed)
                req("observedDirection", t.observedDirection.wire); req("amountMinor", t.amountMinor); opt("originalAmountMinor", t.originalAmountMinor)
                req("currency", t.currency.name); opt("merchantId", t.merchantId); opt("categoryId", t.categoryId)
                req("categoryConfirmed", t.categoryConfirmed); req("excludedFromBudget", t.excludedFromBudget); req("reviewState", t.reviewState.wire)
                opt("note", t.note); opt("walletId", t.walletId); opt("transferToWalletId", t.transferToWalletId)
                opt("statedBalanceMinor", t.statedBalanceMinor); opt("rawDescription", t.rawDescription); opt("rawMerchantName", t.rawMerchantName)
                req("isCashTagged", t.isCashTagged); opt("sourceCategory", t.sourceCategory); opt("sourceOperationType", t.sourceOperationType)
                req("createdAt", t.createdAt); req("updatedAt", t.updatedAt)
            }
        },
        { r ->
            Transaction(
                id = r.str("id"), occurredAt = r.str("occurredAt"), datePrecision = r.str("datePrecision"), sourceOrder = r.int("sourceOrder"),
                economicKind = r.wire("economicKind", EconomicKind::fromWire), economicKindConfirmed = r.bool("economicKindConfirmed"),
                observedDirection = r.wire("observedDirection", Direction::fromWire), amountMinor = r.long("amountMinor"),
                currency = r.wire("currency", Currency::valueOf), categoryConfirmed = r.bool("categoryConfirmed"),
                excludedFromBudget = r.bool("excludedFromBudget"), reviewState = r.wire("reviewState", ReviewState::fromWire),
                isCashTagged = r.bool("isCashTagged"), createdAt = r.str("createdAt"), updatedAt = r.str("updatedAt"),
                sourceTime = r.strOrNull("sourceTime"), originalAmountMinor = r.longOrNull("originalAmountMinor"), merchantId = r.strOrNull("merchantId"),
                categoryId = r.strOrNull("categoryId"), note = r.strOrNull("note"), walletId = r.strOrNull("walletId"),
                transferToWalletId = r.strOrNull("transferToWalletId"), statedBalanceMinor = r.longOrNull("statedBalanceMinor"),
                rawDescription = r.strOrNull("rawDescription"), rawMerchantName = r.strOrNull("rawMerchantName"),
                sourceCategory = r.strOrNull("sourceCategory"), sourceOperationType = r.strOrNull("sourceOperationType"),
            )
        },
    )

    val obligations: DocCodec<Obligation> = codec(
        "obligations", { it.id },
        { o ->
            doc {
                req("id", o.id); req("personId", o.personId); nul("originTransactionId", o.originTransactionId); req("kind", o.kind.wire)
                req("originalMinor", o.originalMinor); req("currency", o.currency.name)
            }
        },
        { r ->
            Obligation(
                r.str("id"), r.str("personId"), r.strOrNull("originTransactionId"), r.wire("kind", ObligationKind::fromWire),
                r.long("originalMinor"), r.wire("currency", Currency::valueOf),
            )
        },
    )

    val allocations: DocCodec<PersonAllocation> = codec(
        "allocations", { it.id },
        { a ->
            doc {
                req("id", a.id); req("transactionId", a.transactionId); req("personId", a.personId); req("allocationKind", a.allocationKind.wire)
                req("amountMinor", a.amountMinor); req("currency", a.currency.name)
            }
        },
        { r ->
            PersonAllocation(
                r.str("id"), r.str("transactionId"), r.str("personId"), r.wire("allocationKind", AllocationKind::fromWire),
                r.long("amountMinor"), r.wire("currency", Currency::valueOf),
            )
        },
    )

    val settlements: DocCodec<Settlement> = codec(
        "settlements", { it.id },
        { s -> doc { req("id", s.id); req("transactionId", s.transactionId); req("obligationId", s.obligationId); req("amountMinor", s.amountMinor) } },
        { r -> Settlement(r.str("id"), r.str("transactionId"), r.str("obligationId"), r.long("amountMinor")) },
    )

    val sourceRecords: DocCodec<SourceRecord> = codec(
        "sourceRecords", { it.id },
        { s ->
            doc {
                req("id", s.id); req("batchId", s.batchId); req("accountIdentity", s.accountIdentity); nul("sourceReference", s.sourceReference)
                req("sourceHash", s.sourceHash); req("originalRowIndex", s.originalRowIndex); req("rawLine", s.rawLine)
                nul("transactionId", s.transactionId); req("matchingState", s.matchingState.wire); req("reason", s.reason)
                // §75-10 (S4): سجل الدمج بس — خريطة متداخلة (النسخة الشاملة في التطبيق القديم بتفحص المستوى الأول بس) وما بتتكتبش من غيرها
                opt(
                    "mergeUndo",
                    s.mergeUndo?.let { m ->
                        doc {
                            req("occurredAt", m.occurredAt); req("sourceOrder", m.sourceOrder); opt("statedBalanceMinor", m.statedBalanceMinor)
                            // اللي سطر الكشف كتبه (مراجعة S4): التراجع بيرجّع الحقل بس لو لسه فيه ده
                            opt("mergedOccurredAt", m.mergedOccurredAt); opt("mergedStatedBalanceMinor", m.mergedStatedBalanceMinor)
                        }
                    },
                )
            }
        },
        { r ->
            SourceRecord(
                r.str("id"), r.str("batchId"), r.str("accountIdentity"), r.strOrNull("sourceReference"), r.str("sourceHash"),
                r.int("originalRowIndex"), r.str("rawLine"), r.strOrNull("transactionId"), r.wire("matchingState", MatchingState::fromWire), r.str("reason"),
                mergeUndo = if (r.has("mergeUndo")) {
                    r.map("mergeUndo").let { m ->
                        MergeRestore(m.str("occurredAt"), m.int("sourceOrder"), m.longOrNull("statedBalanceMinor"), m.strOrNull("mergedOccurredAt"), m.longOrNull("mergedStatedBalanceMinor"))
                    }
                } else {
                    null
                },
            )
        },
    )

    val importBatches: DocCodec<ImportBatch> = codec(
        "importBatches", { it.id },
        { b ->
            val c = b.counts
            doc {
                req("id", b.id); req("sourceType", b.sourceType.wire); req("fileHash", b.fileHash); req("fileName", b.fileName)
                req("importedAt", b.importedAt); req("state", b.state.wire)
                req(
                    "counts",
                    doc {
                        req("total", c.total); req("imported", c.imported); req("duplicates", c.duplicates); req("similar", c.similar)
                        req("conflicts", c.conflicts); req("invalid", c.invalid)
                    },
                )
            }
        },
        { r ->
            val c = r.map("counts")
            ImportBatch(
                r.str("id"), r.wire("sourceType", ImportSourceType::fromWire), r.str("fileHash"), r.str("fileName"), r.str("importedAt"),
                r.wire("state", ImportBatchState::fromWire),
                ImportCounts(c.int("total"), c.int("imported"), c.int("duplicates"), c.int("similar"), c.int("conflicts"), c.int("invalid")),
            )
        },
    )

    val transactionTags: DocCodec<TransactionTag> = codec(
        "transactionTags", { it.id },
        { t -> doc { req("id", t.id); req("transactionId", t.transactionId); req("tagId", t.tagId) } },
        { r -> TransactionTag(r.str("id"), r.str("transactionId"), r.str("tagId")) },
    )
}
