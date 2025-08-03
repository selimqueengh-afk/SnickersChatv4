package com.snickerschat.app.config

import com.cloudinary.Cloudinary
import com.cloudinary.android.MediaManager

object CloudinaryConfig {
    
    private const val CLOUD_NAME = "your_cloud_name" // Cloudinary cloud name - BURAYA CLOUD NAME'İNİ YAZ
    private const val API_KEY = "your_api_key" // Cloudinary API key - BURAYA API KEY'İNİ YAZ
    private const val API_SECRET = "your_api_secret" // Cloudinary API secret - BURAYA API SECRET'INI YAZ
    
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