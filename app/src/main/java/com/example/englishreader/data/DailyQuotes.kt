package com.example.englishreader.data

data class DailyQuote(
    val english: String,
    val chinese: String,
    val author: String,
)

/** 每日一句的内置语料，不依赖网络，按天数顺序轮换。 */
val dailyQuotes = listOf(
    DailyQuote(
        english = "The only way to do great work is to love what you do.",
        chinese = "做出伟大工作的唯一方法，就是热爱你所做的事。",
        author = "Steve Jobs",
    ),
    DailyQuote(
        english = "Stay hungry, stay foolish.",
        chinese = "求知若饥，虚心若愚。",
        author = "Steve Jobs",
    ),
    DailyQuote(
        english = "It always seems impossible until it's done.",
        chinese = "事情在完成之前，看起来总是不可能。",
        author = "Nelson Mandela",
    ),
    DailyQuote(
        english = "The journey of a thousand miles begins with a single step.",
        chinese = "千里之行，始于足下。",
        author = "Lao Tzu",
    ),
    DailyQuote(
        english = "Life is what happens when you're busy making other plans.",
        chinese = "生活就是当你忙于制定其他计划时发生的事。",
        author = "John Lennon",
    ),
    DailyQuote(
        english = "In the middle of difficulty lies opportunity.",
        chinese = "困难之中蕴藏着机会。",
        author = "Albert Einstein",
    ),
    DailyQuote(
        english = "Whether you think you can or you think you can't, you're right.",
        chinese = "无论你认为自己行还是不行，你都是对的。",
        author = "Henry Ford",
    ),
    DailyQuote(
        english = "The future belongs to those who believe in the beauty of their dreams.",
        chinese = "未来属于那些相信梦想之美的人。",
        author = "Eleanor Roosevelt",
    ),
    DailyQuote(
        english = "Do not go where the path may lead, go instead where there is no path and leave a trail.",
        chinese = "不要走现成的路，而要去没有路的地方，留下一条路。",
        author = "Ralph Waldo Emerson",
    ),
    DailyQuote(
        english = "Success is not final, failure is not fatal: it is the courage to continue that counts.",
        chinese = "成功并非终点，失败也非末日，重要的是继续前行的勇气。",
        author = "Winston Churchill",
    ),
    DailyQuote(
        english = "The best time to plant a tree was twenty years ago. The second best time is now.",
        chinese = "种一棵树最好的时间是二十年前，其次是现在。",
        author = "Chinese Proverb",
    ),
    DailyQuote(
        english = "You miss 100% of the shots you don't take.",
        chinese = "你不出手，就百分之百不会进球。",
        author = "Wayne Gretzky",
    ),
    DailyQuote(
        english = "Happiness is not something ready made. It comes from your own actions.",
        chinese = "幸福不是现成的，它来自你自己的行动。",
        author = "Dalai Lama",
    ),
    DailyQuote(
        english = "The mind is everything. What you think you become.",
        chinese = "心即一切，你所想的，你就会成为。",
        author = "Buddha",
    ),
    DailyQuote(
        english = "What we know is a drop, what we don't know is an ocean.",
        chinese = "我们所知不过一滴水，未知则是海洋。",
        author = "Isaac Newton",
    ),
    DailyQuote(
        english = "Simplicity is the ultimate sophistication.",
        chinese = "简约是终极的精致。",
        author = "Leonardo da Vinci",
    ),
    DailyQuote(
        english = "The unexamined life is not worth living.",
        chinese = "未经审视的人生不值得过。",
        author = "Socrates",
    ),
    DailyQuote(
        english = "It is never too late to be what you might have been.",
        chinese = "成为你想成为的人，永远不会太晚。",
        author = "George Eliot",
    ),
    DailyQuote(
        english = "We are what we repeatedly do. Excellence, then, is not an act, but a habit.",
        chinese = "我们反复做的事造就了我们。因此，卓越不是一种行为，而是一种习惯。",
        author = "Aristotle",
    ),
    DailyQuote(
        english = "The only true wisdom is in knowing you know nothing.",
        chinese = "唯一真正的智慧，是知道自己一无所知。",
        author = "Socrates",
    ),
    DailyQuote(
        english = "A room without books is like a body without a soul.",
        chinese = "没有书的房间，就像没有灵魂的身体。",
        author = "Cicero",
    ),
    DailyQuote(
        english = "Reading is to the mind what exercise is to the body.",
        chinese = "阅读之于心灵，正如运动之于身体。",
        author = "Joseph Addison",
    ),
    DailyQuote(
        english = "Not all those who wander are lost.",
        chinese = "并非所有流浪者都迷失了方向。",
        author = "J.R.R. Tolkien",
    ),
    DailyQuote(
        english = "Words are, in my not-so-humble opinion, our most inexhaustible source of magic.",
        chinese = "言语是我们取之不尽的魔法之源。",
        author = "J.K. Rowling",
    ),
    DailyQuote(
        english = "There is no friend as loyal as a book.",
        chinese = "没有比书更忠诚的朋友。",
        author = "Ernest Hemingway",
    ),
    DailyQuote(
        english = "The more that you read, the more things you will know. The more that you learn, the more places you'll go.",
        chinese = "你读得越多，知道的就越多；你学得越多，能去的地方就越多。",
        author = "Dr. Seuss",
    ),
)

/** 一年中的第几天决定当天的句子，“换一换”通过 offset 顺延。 */
fun dailyQuoteIndex(dayOfYear: Int, offset: Int): Int =
    ((dayOfYear - 1 + offset) % dailyQuotes.size + dailyQuotes.size) % dailyQuotes.size
