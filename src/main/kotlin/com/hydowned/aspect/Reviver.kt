package com.hydowned.aspect

interface Reviver : Aspect {
    fun canRevive(target: Downable): Boolean
    fun getReviveDuration(): Double
    fun startRevive(target: Downable)
    fun cancelRevive()
    fun finishRevive()
}
