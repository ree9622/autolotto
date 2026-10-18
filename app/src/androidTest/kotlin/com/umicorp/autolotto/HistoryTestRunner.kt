package com.umicorp.autolotto

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Isolate read-only UI tests from saved accounts, background jobs and all live services. */
class HistoryTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
