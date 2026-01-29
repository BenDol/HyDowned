package com.hydowned.aspect

interface Downable : Aspect {
    fun isDowned(): Boolean
    fun isDead(): Boolean
    fun isAlive(): Boolean
    fun getDownProgress(): Float
    fun getDownDuration(): Double
    fun getTimeRemaining(): Double
    fun getAggressor(): Aspect?
    fun getCurrentReviver(): Reviver?
    fun onDown(aggressor: Aspect)
    fun onDeath()
    fun onRevived()
    fun onCancelDown()
    fun canBeRevivedBy(reviver: Reviver): Boolean
    fun canDie(): Boolean
    fun tick()
}
