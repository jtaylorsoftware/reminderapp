package com.github.jeremyt135.reminderapp.data

interface DomainModel<out T> {
    fun asDataModel(): T
}

interface DataModel<out T> {
    fun asDomainModel(): T
}

fun <T> List<DataModel<T>>.asDomainModel(): List<T> = map { it.asDomainModel() }
fun <T> List<DomainModel<T>>.asDataModel(): List<T> = map { it.asDataModel() }