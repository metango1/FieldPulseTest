/*
 * Copyright 2015 - 2021 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarar.FieldPulse

import android.content.Context
import org.tarar.FieldPulse.ProtocolFormatter.formatRequest
import org.tarar.FieldPulse.RequestManager.sendRequestAsync
import org.tarar.FieldPulse.PositionProvider.PositionListener
import org.tarar.FieldPulse.NetworkManager.NetworkHandler
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import android.util.Log
import org.tarar.FieldPulse.DatabaseHelper.DatabaseHandler
import org.tarar.FieldPulse.RequestManager.RequestHandler

class TrackingController(private val context: Context) : PositionListener, NetworkHandler {

    private val handler = Handler(Looper.getMainLooper())
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val positionProvider = org.tarar.FieldPulse.PositionProviderFactory.create(context, this)
    private val databaseHelper = org.tarar.FieldPulse.DatabaseHelper(context)
    private val networkManager = org.tarar.FieldPulse.NetworkManager(context, this)

    private val url: String = preferences.getString(
        org.tarar.FieldPulse.MainFragment.Companion.KEY_URL, context.getString(
            org.tarar.FieldPulse.R.string.settings_url_default_value
        ))!!
    private val buffer: Boolean = preferences.getBoolean(org.tarar.FieldPulse.MainFragment.Companion.KEY_BUFFER, true)

    private var isOnline = networkManager.isOnline
    private var isWaiting = false

    fun start() {
        if (isOnline) {
            read()
        }
        try {
            positionProvider.startUpdates()
        } catch (e: SecurityException) {
            Log.w(org.tarar.FieldPulse.TrackingController.Companion.TAG, e)
        }
        networkManager.start()
    }

    fun stop() {
        networkManager.stop()
        try {
            positionProvider.stopUpdates()
        } catch (e: SecurityException) {
            Log.w(org.tarar.FieldPulse.TrackingController.Companion.TAG, e)
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPositionUpdate(position: org.tarar.FieldPulse.Position) {
        org.tarar.FieldPulse.StatusActivity.Companion.addMessage(context.getString(org.tarar.FieldPulse.R.string.status_location_update))
        if (buffer) {
            write(position)
        } else {
            send(position)
        }
    }

    override fun onPositionError(error: Throwable) {}
    override fun onNetworkUpdate(isOnline: Boolean) {
        val message = if (isOnline) org.tarar.FieldPulse.R.string.status_network_online else org.tarar.FieldPulse.R.string.status_network_offline
        org.tarar.FieldPulse.StatusActivity.Companion.addMessage(context.getString(message))
        if (!this.isOnline && isOnline) {
            read()
        }
        this.isOnline = isOnline
    }

    //
    // State transition examples:
    //
    // write -> read -> send -> delete -> read
    //
    // read -> send -> retry -> read -> send
    //

    private fun log(action: String, position: org.tarar.FieldPulse.Position?) {
        var formattedAction: String = action
        if (position != null) {
            formattedAction +=
                    " (id:" + position.id +
                    " time:" + position.time.time / 1000 +
                    " lat:" + position.latitude +
                    " lon:" + position.longitude + ")"
        }
        Log.d(org.tarar.FieldPulse.TrackingController.Companion.TAG, formattedAction)
    }

    private fun write(position: org.tarar.FieldPulse.Position) {
        log("write", position)
        databaseHelper.insertPositionAsync(position, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    if (isOnline && isWaiting) {
                        read()
                        isWaiting = false
                    }
                }
            }
        })
    }

    private fun read() {
        log("read", null)
        databaseHelper.selectPositionAsync(object : DatabaseHandler<org.tarar.FieldPulse.Position?> {
            override fun onComplete(success: Boolean, result: org.tarar.FieldPulse.Position?) {
                if (success) {
                    if (result != null) {
                        if (result.deviceId == preferences.getString(org.tarar.FieldPulse.MainFragment.Companion.KEY_DEVICE, null)) {
                            send(result)
                        } else {
                            delete(result)
                        }
                    } else {
                        isWaiting = true
                    }
                } else {
                    retry()
                }
            }
        })
    }

    private fun delete(position: org.tarar.FieldPulse.Position) {
        log("delete", position)
        databaseHelper.deletePositionAsync(position.id, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    read()
                } else {
                    retry()
                }
            }
        })
    }

    private fun send(position: org.tarar.FieldPulse.Position) {
        log("send", position)
        val request = formatRequest(url, position)
        sendRequestAsync(request, object : RequestHandler {
            override fun onComplete(success: Boolean) {
                if (success) {
                    if (buffer) {
                        delete(position)
                    }
                } else {
                    org.tarar.FieldPulse.StatusActivity.Companion.addMessage(context.getString(org.tarar.FieldPulse.R.string.status_send_fail))
                    if (buffer) {
                        retry()
                    }
                }
            }
        })
    }

    private fun retry() {
        log("retry", null)
        handler.postDelayed({
            if (isOnline) {
                read()
            }
        }, org.tarar.FieldPulse.TrackingController.Companion.RETRY_DELAY.toLong())
    }

    companion object {
        private val TAG = org.tarar.FieldPulse.TrackingController::class.java.simpleName
        private const val RETRY_DELAY = 30 * 1000
    }

}
