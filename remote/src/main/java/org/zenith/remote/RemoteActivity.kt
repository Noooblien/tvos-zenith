package org.zenith.remote

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class RemoteActivity : Activity() {

    private lateinit var etTvIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var remoteWebView: WebView
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)

        etTvIp = findViewById(R.id.etTvIp)
        btnConnect = findViewById(R.id.btnConnect)
        remoteWebView = findViewById(R.id.remoteWebView)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        setupWebView()

        val prefs = getSharedPreferences("zenith_remote_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("last_tv_ip", "") ?: ""
        if (savedIp.isNotEmpty()) {
            etTvIp.setText(savedIp)
            connectToTv(savedIp)
        } else {
            tvEmptyState.visibility = View.VISIBLE
        }

        btnConnect.setOnClickListener {
            val ip = etTvIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                prefs.edit().putString("last_tv_ip", ip).apply()
                connectToTv(ip)
            } else {
                Toast.makeText(this, "Please enter TV IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebView() {
        val settings = remoteWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        remoteWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tvEmptyState.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                tvEmptyState.text = "Could not connect to Zenith TV at $failingUrl\n\nEnsure Zenith TV is running on the TV and phone is on the same Wi-Fi."
                tvEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun connectToTv(ip: String) {
        val cleanIp = ip.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val targetUrl = "http://$cleanIp:8080"
        remoteWebView.loadUrl(targetUrl)
    }

    override fun onBackPressed() {
        if (remoteWebView.canGoBack()) {
            remoteWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
