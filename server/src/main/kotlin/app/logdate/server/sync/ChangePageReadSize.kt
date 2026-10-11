package app.logdate.server.sync

/**
 * How many rows to read for a change page of [limit] records.
 *
 * Change feeds read one row past the page; if it exists, the response says another page follows.
 * Callers pass [Int.MAX_VALUE] to read a whole feed, and adding one to that wraps to a negative
 * number, which Postgres rejects as `LIMIT must not be negative`. A whole-feed read has no next
 * page to detect, so it reads [Int.MAX_VALUE] rows.
 */
internal fun changePageReadSize(limit: Int): Int = if (limit == Int.MAX_VALUE) limit else limit + 1
