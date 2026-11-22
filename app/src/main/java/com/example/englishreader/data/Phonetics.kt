package com.example.englishreader.data

/** 一个国际音标及其代表单词。 */
data class Phonetic(
    val symbol: String,
    val word: String,
    val meaning: String,
)

/** 元音 20 个：12 个单元音 + 8 个双元音。 */
val vowelPhonetics = listOf(
    Phonetic("/iː/", "see", "看见"),
    Phonetic("/ɪ/", "sit", "坐"),
    Phonetic("/e/", "bed", "床"),
    Phonetic("/æ/", "cat", "猫"),
    Phonetic("/ɑː/", "car", "汽车"),
    Phonetic("/ɒ/", "hot", "热的"),
    Phonetic("/ɔː/", "door", "门"),
    Phonetic("/ʊ/", "book", "书"),
    Phonetic("/uː/", "food", "食物"),
    Phonetic("/ʌ/", "cup", "杯子"),
    Phonetic("/ɜː/", "bird", "鸟"),
    Phonetic("/ə/", "about", "关于"),
    Phonetic("/eɪ/", "day", "天"),
    Phonetic("/aɪ/", "time", "时间"),
    Phonetic("/ɔɪ/", "boy", "男孩"),
    Phonetic("/aʊ/", "now", "现在"),
    Phonetic("/əʊ/", "go", "去"),
    Phonetic("/ɪə/", "here", "这里"),
    Phonetic("/eə/", "hair", "头发"),
    Phonetic("/ʊə/", "tour", "旅行"),
)

/** 辅音 28 个。 */
val consonantPhonetics = listOf(
    Phonetic("/p/", "pen", "钢笔"),
    Phonetic("/b/", "bag", "包"),
    Phonetic("/t/", "tea", "茶"),
    Phonetic("/d/", "dog", "狗"),
    Phonetic("/k/", "key", "钥匙"),
    Phonetic("/ɡ/", "go", "去"),
    Phonetic("/f/", "five", "五"),
    Phonetic("/v/", "very", "非常"),
    Phonetic("/θ/", "think", "想"),
    Phonetic("/ð/", "this", "这个"),
    Phonetic("/s/", "sun", "太阳"),
    Phonetic("/z/", "zoo", "动物园"),
    Phonetic("/ʃ/", "she", "她"),
    Phonetic("/ʒ/", "measure", "测量"),
    Phonetic("/h/", "hat", "帽子"),
    Phonetic("/tʃ/", "chair", "椅子"),
    Phonetic("/dʒ/", "job", "工作"),
    Phonetic("/tr/", "tree", "树"),
    Phonetic("/dr/", "dream", "梦想"),
    Phonetic("/ts/", "cats", "猫（复数）"),
    Phonetic("/dz/", "beds", "床（复数）"),
    Phonetic("/m/", "man", "男人"),
    Phonetic("/n/", "no", "不"),
    Phonetic("/ŋ/", "sing", "唱歌"),
    Phonetic("/l/", "like", "喜欢"),
    Phonetic("/r/", "red", "红色"),
    Phonetic("/j/", "yes", "是的"),
    Phonetic("/w/", "we", "我们"),
)
