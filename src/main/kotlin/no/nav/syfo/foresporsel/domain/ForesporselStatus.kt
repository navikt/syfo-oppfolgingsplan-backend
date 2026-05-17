package no.nav.syfo.foresporsel.domain

enum class ForesporselStatus {
    CAN_REQUEST,
    ALREADY_REQUESTED,
    HAS_ACTIVE_PLAN,
    MISSING_NARMESTELEDER,
    NARMESTELEDER_UNKNOWN,
}
