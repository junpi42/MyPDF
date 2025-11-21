package com.example.mypdf

enum class Language {
    EN,
    ES
}

fun Language.toIsEnglish(): Boolean = this == Language.EN

fun Boolean.toLanguage(): Language = if (this) Language.EN else Language.ES
