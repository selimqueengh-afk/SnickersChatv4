package com.snickerschat.app.config

import com.cloudinary.Cloudinary
import com.cloudinary.android.MediaManager

object CloudinaryConfig {
    
    private const val CLOUD_NAME = "dedz2kgln"
    private const val API_KEY = "934616664355316"
    private const val API_SECRET = "ejpJuQ9pIY9ueCmCNvZAwQ4jWho"
    
    fun init() {
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUD_NAME
        config["api_key"] = API_KEY
        config["api_secret"] = API_SECRET
        
        MediaManager.init(android.content.Context, config)
    }
    
    fun getCloudinary(): Cloudinary {
        return Cloudinary(CLOUD_NAME)
    }
}