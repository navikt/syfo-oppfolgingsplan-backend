package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.util.logger

private const val SOFT_DELETE_MAX_BATCH_ITERATIONS = 1000

internal suspend fun runSoftDeleteBatchLoop(
    maxBatchIterations: Int = SOFT_DELETE_MAX_BATCH_ITERATIONS,
    softDeleteBatch: suspend () -> Int,
): Int {
    require(maxBatchIterations > 0) {
        "maxBatchIterations must be greater than 0"
    }

    var total = 0
    repeat(maxBatchIterations) {
        val count = softDeleteBatch()
        total += count
        if (count == 0) {
            return total
        }
    }

    logger("SoftDeleteBatchLoop").warn(
        "Stopped soft-delete loop after reaching safeguard of $maxBatchIterations batches; " +
            "total soft-deleted so far: $total",
    )
    return total
}
