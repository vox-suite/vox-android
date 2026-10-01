package `in`.voxagent.mobile.sms

import android.content.Context
import android.provider.Telephony
import java.time.Instant

class SmsReader(private val context: Context) {

    /** The newest [limit] messages, returned oldest first. Used for the very first sync. */
    fun readLatest(limit: Int): List<SmsMessage> = query(null, null, "DESC", limit).reversed()

    fun readSince(sinceEpochMillis: Long, limit: Int): List<SmsMessage> =
        query("${Telephony.Sms.DATE} > ?", arrayOf(sinceEpochMillis.toString()), "ASC", limit)

    private fun query(
        selection: String?,
        selectionArgs: Array<String>?,
        order: String,
        limit: Int,
    ): List<SmsMessage> {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        val messages = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} $order LIMIT $limit",
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: continue
                val dateMillis = cursor.getLong(dateIndex)
                messages.add(
                    SmsMessage(
                        sender = sender,
                        body = body,
                        received_at = Instant.ofEpochMilli(dateMillis).toString(),
                    ),
                )
            }
        }
        return messages
    }

    fun latestTimestampMillis(): Long {
        val projection = arrayOf(Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
            }
        }
        return 0L
    }
}
