package com.mafocus.app

import android.content.Context

/** 30 strict-mode roasts. First = brutal, second = extra brutal. {name} {count} {mins} are filled in. */
object Roasts {
    private val R = listOf(
        "{name}, {count} reels today. You'd call that a hobby if it paid anything." to
            "{name}, {count} reels. That's {mins} minutes of your life spent watching strangers live theirs.",
        "Your future self is watching this and quietly giving up on you." to
            "Your future self already stopped waiting for you. You keep proving them right.",
        "Another reel? Your goals are collecting dust and so are your excuses." to
            "Your goals stopped waiting, {name}. You're a spectator in your own life now.",
        "{name}, you've scrolled {count} times and still haven't done the one thing you promised." to
            "{count} reels, zero tasks done. That's not tired, {name}. That's choosing to fail.",
        "Everyone you envy closed this app hours ago." to
            "Everyone you envy is working right now while you scroll. Keep envying, it's all you're doing.",
        "That's {mins} minutes spent on strangers who don't know you exist." to
            "{mins} minutes given to strangers who'd never give you one. You're their best customer and their biggest fool.",
        "You're not tired, {name}. You're avoiding. Be honest." to
            "You're not tired, {name}. You're scared. Scared to start, so you scroll.",
        "Your thumb is the only part of you that's working today." to
            "Your thumb is the only thing you've trained today, and it's training you to stay exactly where you are.",
        "Nobody ever got successful by watching others succeed." to
            "You're watching other people live while yours stands still. Front-row seat to your own stagnation.",
        "Close it. Your potential is embarrassed to be associated with you right now." to
            "Your potential didn't leave, {name}. You're just spending it {count} reels at a time.",
        "{name}, your phone tracks your habits better than you track your goals." to
            "Your phone shows more discipline tracking you than you show living.",
        "Imagine telling your family you spent today on reels. Go on, say it out loud." to
            "Imagine your parents seeing this screen. All their effort, and you chose {count} reels.",
        "Exams don't care about your reels, {name}." to
            "The reels won't sit your exam, {name}. Fail it scrolling and see if they send a refund.",
        "{count} reels. Zero of them will matter tomorrow." to
            "{count} reels and not one will matter in a week. Your results will. Think about that.",
        "You said 'just one more'. Liar." to
            "You said 'just one more' {count} times today. Your word means nothing to you, {name}.",
        "Keep scrolling, {name}. Mediocrity needs loyal customers." to
            "Keep scrolling. The world needs average people to watch the ones who weren't.",
        "Your brain is melting and you're helping it along." to
            "Your brain isn't melting, {name}. It's being strip-mined and you're holding the shovel.",
        "Close the app or admit you have zero self-control." to
            "Close the app or stop pretending you want anything more than this feed.",
        "Somebody your age is building something right now. You're watching someone dance." to
            "Someone your age just earned a win while you watched reel number {count}. Look at that gap.",
        "{name}, this is the part where you pretend you'll start tomorrow." to
            "Tomorrow, {name}? You said that yesterday too. Tomorrow is where your plans go to die.",
        "All that scrolling and you still don't feel better. Funny, that." to
            "You feel worse after every reel and still go back. That's not entertainment, {name}, that's a leash.",
        "Imagine reading your screen-time report out loud to someone you respect." to
            "Read today's screen time to someone you respect. Out loud. I dare you.",
        "Your bed has seen more ambition today than you have." to
            "Your couch has seen more ambition than you, and it didn't even try.",
        "{mins} minutes on reels and you remember none of it." to
            "{mins} minutes and nothing stuck. You didn't watch, {name}. You got farmed.",
        "You're a lab rat and the cheese is a dancing stranger." to
            "You're a lab rat pressing a lever for scraps, {name}, and you say thank you.",
        "Winners already closed this app. You're still here." to
            "The people you want to become closed this app an hour ago. You're still choosing who you're not.",
        "{name}, your goals called. You didn't pick up." to
            "Your goals stopped calling, {name}. They've moved on to people who show up.",
        "Stop. Do one thing. Prove me wrong." to
            "Stop. Do one thing. You've proved me right {count} times already today.",
        "That scroll was cute. Now put the phone down." to
            "That scroll was pathetic. Put the phone down before you do it again.",
        "You're better than this feed, {name}. Act like it." to
            "You are wasting yourself, {name}. Stop, or admit this is who you've decided to be."
    )

    /** i = which roast, hit = how many roasts you've already had (used to escalate). */
    fun pick(c: Context, i: Int, hit: Int): String {
        val pair = R[i % R.size]
        val extra = when (Store.rlevel(c)) { 0 -> false; 1 -> true; else -> hit >= 2 }
        return fill(c, if (extra) pair.second else pair.first)
    }

    fun fill(c: Context, s: String): String {
        val nm = Store.userName(c).trim()
        var rt = 0L
        Store.APPS.keys.forEach { rt += Store.time(c, "r", it) }
        val mins = if (rt < 60000) "countless" else (rt / 60000).toString()
        val out = s.replace("{name}, ", if (nm.isEmpty()) "" else "$nm, ")
            .replace("{name}", if (nm.isEmpty()) "you" else nm)
            .replace("{count}", Store.count(c).toString())
            .replace("{mins}", mins)
        return out.replaceFirstChar { it.uppercase() }
    }
}
