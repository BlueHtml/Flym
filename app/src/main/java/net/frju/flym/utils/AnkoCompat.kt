/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.frju.flym.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import java.io.Serializable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val ASYNC_EXECUTOR: ExecutorService = Executors.newCachedThreadPool()
private val MAIN_HANDLER = Handler(Looper.getMainLooper())

/** Lightweight replacements for the small subset of Anko utilities used by Flym. */
fun doAsync(block: () -> Unit) {
    ASYNC_EXECUTOR.execute(block)
}

fun uiThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        MAIN_HANDLER.post(block)
    }
}

val Context.layoutInflater: LayoutInflater
    get() = LayoutInflater.from(this)

val Activity.inputMethodManager: InputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

val Context.connectivityManager: android.net.ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

val Context.defaultSharedPreferences: SharedPreferences
    get() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)

val Fragment.defaultSharedPreferences: SharedPreferences
    get() = requireContext().defaultSharedPreferences

val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

fun Context.dip(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dip(value: Int): Int = context.dip(value)
fun Fragment.dip(value: Int): Int = requireContext().dip(value)

fun View.onClick(listener: (View) -> Unit) {
    setOnClickListener(listener)
}

fun View.onLongClick(listener: (View) -> Boolean) {
    setOnLongClickListener(listener)
}

fun TextView.onEditorAction(listener: (TextView, Int, KeyEvent?) -> Boolean) {
    setOnEditorActionListener(listener)
}

class TextChangedListenerBuilder {
    internal var afterTextChanged: ((Editable?) -> Unit)? = null

    fun afterTextChanged(listener: (Editable?) -> Unit) {
        afterTextChanged = listener
    }
}

fun TextView.textChangedListener(init: TextChangedListenerBuilder.() -> Unit) {
    val builder = TextChangedListenerBuilder().apply(init)
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            builder.afterTextChanged?.invoke(s)
        }
    })
}

inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any?>) {
    val intent = Intent(this, T::class.java)
    putIntentExtras(intent, params)
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

inline fun <reified T : Activity> Fragment.startActivity(vararg params: Pair<String, Any?>) {
    requireContext().startActivity<T>(*params)
}

@PublishedApi
internal fun putIntentExtras(intent: Intent, params: Array<out Pair<String, Any?>>) {
    params.forEach { (key, value) ->
        when (value) {
            null -> intent.putExtra(key, null as String?)
            is String -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
            is Long -> intent.putExtra(key, value)
            is Boolean -> intent.putExtra(key, value)
            is Float -> intent.putExtra(key, value)
            is Double -> intent.putExtra(key, value)
            is CharSequence -> intent.putExtra(key, value)
            is android.os.Parcelable -> intent.putExtra(key, value)
            is Serializable -> intent.putExtra(key, value)
            else -> throw IllegalArgumentException("Unsupported Intent extra type: ${value::class.java.name}")
        }
    }
}

fun Context.browse(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    uiThread {
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No application can open this link", Toast.LENGTH_LONG).show()
        }
    }
}

fun Fragment.browse(url: String) = requireContext().browse(url)

fun Context.share(text: String, title: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    uiThread {
        startActivity(Intent.createChooser(intent, title))
    }
}

fun Fragment.share(text: String, title: String? = null) = requireContext().share(text, title)

fun Context.toast(@StringRes message: Int): Toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
fun Context.toast(message: CharSequence): Toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
fun Fragment.toast(@StringRes message: Int): Toast = requireContext().toast(message)
fun Fragment.toast(message: CharSequence): Toast = requireContext().toast(message)

fun View.snackbar(@StringRes message: Int) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

fun Context.attr(@AttrRes resourceId: Int): TypedValue {
    return TypedValue().also {
        if (!theme.resolveAttribute(resourceId, it, true)) {
            it.resourceId = 0
            it.data = 0
        }
    }
}

@ColorInt
fun Context.colorAttr(@AttrRes resourceId: Int): Int {
    val value = attr(resourceId)
    return if (value.resourceId != 0) {
        ContextCompat.getColor(this, value.resourceId)
    } else {
        value.data
    }
}

var ImageView.imageResource: Int
    get() = 0
    set(value) {
        setImageResource(value)
    }

var View.backgroundColor: Int
    get() = 0
    set(value) {
        setBackgroundColor(value)
    }

