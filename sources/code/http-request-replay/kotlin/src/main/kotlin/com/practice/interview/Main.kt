package com.practice.interview

import com.google.gson.GsonBuilder

import okhttp3.OkHttpClient

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        useGSONForSomeReason("hello\n")
        println("Hello world!")
    }

    fun useGSONForSomeReason(input: String?) {
        GsonBuilder().create().toJson(input, System.out)
    }
}