package com.callbackdev.saldo.feature.transactions.importer

/**
 * A tolerant RFC 4180 CSV reader, the counterpart of the export's builder.
 *
 * It is deliberately forgiving so a file that did not come from Saldo still
 * parses: a leading UTF-8 BOM is stripped, `\r\n`, `\n` and lone `\r` all end a
 * record, a quote inside a quoted field is doubled to escape it, and stray
 * quotes in an unquoted field are kept verbatim rather than rejected. The
 * column [separator] is provided by the caller (see [CsvSeparatorSniffer]).
 */
object CsvReader {

    private const val BOM = '﻿'
    private const val QUOTE = '"'

    /**
     * Splits [text] into records, each a list of raw field strings. Empty input
     * yields an empty list; a trailing newline does not produce a spurious empty
     * record. Fields are returned untrimmed: trimming is a mapping concern.
     */
    fun parse(text: String, separator: Char): List<List<String>> {
        val content = text.removePrefix(BOM.toString())
        if (content.isEmpty()) return emptyList()

        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = mutableListOf()
        }

        while (index < content.length) {
            val char = content[index]
            when {
                inQuotes -> when {
                    char == QUOTE && content.getOrNull(index + 1) == QUOTE -> {
                        field.append(QUOTE)
                        index++
                    }
                    char == QUOTE -> inQuotes = false
                    else -> field.append(char)
                }

                char == QUOTE -> inQuotes = true
                char == separator -> endField()
                char == '\n' -> endRecord()
                char == '\r' -> {
                    endRecord()
                    if (content.getOrNull(index + 1) == '\n') index++
                }

                else -> field.append(char)
            }
            index++
        }
        // Flush the last record unless the file ended exactly on a line break
        // (in which case there is nothing pending).
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
