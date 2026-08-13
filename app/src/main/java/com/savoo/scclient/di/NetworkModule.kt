package com.savoo.scclient.di

import android.content.Context
import android.content.SharedPreferences
import com.savoo.scclient.data.local.AppDatabase
import com.savoo.scclient.data.local.ExcludedArtistDao
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.local.TelegramImportDao
import com.savoo.scclient.data.remote.AuthInterceptor
import com.savoo.scclient.data.remote.ConnectivityEventBus
import com.savoo.scclient.data.remote.GitHubReleaseApi
import com.savoo.scclient.data.remote.LyricsApi
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.debug.DebugLog
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.savoo.scclient.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LyricsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api-v2.soundcloud.com/"

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("sc_client_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    @PlainHttpClient
    fun providePlainOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (e: java.io.IOException) {
                    ConnectivityEventBus.notifyUnreachable()
                    throw e
                }
            }
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor(
                HttpLoggingInterceptor.Logger { message -> DebugLog.log("HTTP", message) },
            )
            builder.addInterceptor { chain ->
                logging.level = if (DebugLog.verboseNetworkLogging.value) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
                logging.intercept(chain)
            }
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideSoundCloudApi(retrofit: Retrofit): SoundCloudApi =
        retrofit.create(SoundCloudApi::class.java)

    @Provides
    @Singleton
    @LyricsRetrofit
    fun provideLyricsRetrofit(@PlainHttpClient client: OkHttpClient, moshi: Moshi): Retrofit {
        val uaClient = client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "Resona (unofficial SoundCloud client)").build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(uaClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsApi(@LyricsRetrofit retrofit: Retrofit): LyricsApi =
        retrofit.create(LyricsApi::class.java)

    @Provides
    @Singleton
    @GitHubRetrofit
    fun provideGitHubRetrofit(@PlainHttpClient client: OkHttpClient, moshi: Moshi): Retrofit {
        val uaClient = client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Resona (unofficial SoundCloud client)")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(uaClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubReleaseApi(@GitHubRetrofit retrofit: Retrofit): GitHubReleaseApi =
        retrofit.create(GitHubReleaseApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.create(context)

    @Provides
    fun provideFavoritesDao(db: AppDatabase): FavoritesDao = db.favoritesDao()

    @Provides
    fun provideOfflineDao(db: AppDatabase): OfflineDao = db.offlineDao()

    @Provides
    fun provideTelegramImportDao(db: AppDatabase): TelegramImportDao = db.telegramImportDao()

    @Provides
    fun providePlayHistoryDao(db: AppDatabase): PlayHistoryDao = db.playHistoryDao()

    @Provides
    fun provideExcludedArtistDao(db: AppDatabase): ExcludedArtistDao = db.excludedArtistDao()
}
