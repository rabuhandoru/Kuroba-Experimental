package com.github.adamantcheese.database.source.cache

interface SuspendableCacheSource<Key, Value> {
    suspend fun get(key: Key): Value?
    suspend fun store(key: Key, value: Value)
    suspend fun contains(key: Key): Boolean
    suspend fun delete(key: Key)
}