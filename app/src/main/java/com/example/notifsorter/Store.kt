package com.example.notifsorter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

data class Rec(
    val id: Long = 0,
    val nkey: String,
    val pkg: String,
    val app: String,
    val title: String,
    val body: String,
    val ts: Long,
    val score: Float,
    val decision: Int,
    val hidden: Boolean,
    val userLabel: Int? = null,   // 1 = important, 0 = not important (your correction)
)

/** What the user sees: your correction wins over the model's guess. */
val Rec.isImportant: Boolean
    get() = userLabel?.let { it == 1 } ?: (decision != Decision.SILENCE)

/** Local SQLite store: notification log + "instant rule" overrides from your corrections. */
class Store private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "notifs.db", null, 1) {

    /** Bumped on every write so the UI reloads. */
    val version = MutableStateFlow(0)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notifs(id INTEGER PRIMARY KEY AUTOINCREMENT, nkey TEXT, pkg TEXT, app TEXT, " +
                "title TEXT, body TEXT, ts INTEGER, score REAL, decision INTEGER, hidden INTEGER, user_label INTEGER)"
        )
        db.execSQL("CREATE TABLE overrides(nkey TEXT PRIMARY KEY, label INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized
    fun insert(r: Rec): Long {
        val cv = ContentValues().apply {
            put("nkey", r.nkey); put("pkg", r.pkg); put("app", r.app)
            put("title", r.title); put("body", r.body); put("ts", r.ts)
            put("score", r.score.toDouble()); put("decision", r.decision)
            put("hidden", if (r.hidden) 1 else 0)
            if (r.userLabel != null) put("user_label", r.userLabel)
        }
        val id = writableDatabase.insert("notifs", null, cv)
        version.value++
        return id
    }

    @Synchronized
    fun recent(limit: Int = 500): List<Rec> {
        val out = ArrayList<Rec>()
        readableDatabase.rawQuery(
            "SELECT id,nkey,pkg,app,title,body,ts,score,decision,hidden,user_label " +
                "FROM notifs ORDER BY ts DESC LIMIT ?", arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Rec(
                        id = c.getLong(0), nkey = c.getString(1), pkg = c.getString(2), app = c.getString(3),
                        title = c.getString(4), body = c.getString(5), ts = c.getLong(6),
                        score = c.getFloat(7), decision = c.getInt(8), hidden = c.getInt(9) == 1,
                        userLabel = if (c.isNull(10)) null else c.getInt(10),
                    )
                )
            }
        }
        return out
    }

    /** Save a correction AND remember it as an instant rule for future notifications. */
    @Synchronized
    fun setUserLabel(id: Long, label: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE notifs SET user_label=? WHERE id=?", arrayOf<Any>(label, id))
        db.rawQuery("SELECT nkey FROM notifs WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                db.execSQL(
                    "INSERT OR REPLACE INTO overrides(nkey,label) VALUES(?,?)",
                    arrayOf<Any>(c.getString(0), label)
                )
            }
        }
        version.value++
    }

    @Synchronized
    fun override(nkey: String): Int? =
        readableDatabase.rawQuery("SELECT label FROM overrides WHERE nkey=?", arrayOf(nkey)).use {
            if (it.moveToFirst()) it.getInt(0) else null
        }

    @Synchronized
    fun labeled(): List<Rec> = recent(100000).filter { it.userLabel != null }

    @Synchronized
    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM notifs")
        writableDatabase.execSQL("DELETE FROM overrides")
        version.value++
    }

    companion object {
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store =
            inst ?: synchronized(this) { inst ?: Store(ctx).also { inst = it } }
    }
}
