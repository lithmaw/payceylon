package com.payceylon.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

// The on-device copy of the signed-in user's wallet. The API (PostgreSQL behind it) is the
// source of truth; these tables hold its last successful answers so the app opens instantly
// and still shows the wallet without a connection. Money is stored as decimal strings.

@Entity(tableName = "profile")
data class ProfileEntity(
    /** Single row: one signed-in user per device. */
    @PrimaryKey val id: Int = 0,
    val name: String,
    val handle: String,
    val phone: String,
    val email: String,
    val photoUrl: String?,
    /** The downloaded profile photo, so it shows offline too. */
    val photo: ByteArray?,
    /** When this snapshot was saved, in epoch milliseconds. */
    val savedAt: Long,
)

@Entity(tableName = "balances")
data class BalanceEntity(
    @PrimaryKey val asset: String,
    val amount: String,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val counterpartyName: String,
    val counterpartyHandle: String,
    val direction: String,
    val currency: String,
    val amount: String,
    val wallet: String,
    val timestamp: Long,
    val toCurrency: String?,
    val toAmount: String?,
    val fee: String?,
    val feeCurrency: String?,
    val reference: String,
    val status: String,
    val txHash: String?,
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val transactionId: String?,
    val read: Boolean,
)

@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val last4: String,
    val holder: String,
    val detail: String,
    val brand: String?,
    /** Keeps the server's order. */
    val position: Int,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
)

@Entity(tableName = "addresses")
data class AddressEntity(
    /** Token symbol, e.g. "USDT". */
    @PrimaryKey val token: String,
    val address: String,
)

@Dao
interface WalletDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun profile(): ProfileEntity?

    @Query("SELECT * FROM balances")
    suspend fun balances(): List<BalanceEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun notifications(): List<NotificationEntity>

    @Query("SELECT * FROM payment_methods ORDER BY position")
    suspend fun paymentMethods(): List<PaymentMethodEntity>

    @Query("SELECT * FROM settings")
    suspend fun settings(): List<SettingEntity>

    @Query("SELECT * FROM addresses")
    suspend fun addresses(): List<AddressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddresses(addresses: List<AddressEntity>)

    @Query("DELETE FROM addresses")
    suspend fun deleteAddresses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalances(balances: List<BalanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethods(methods: List<PaymentMethodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: List<SettingEntity>)

    @Query("DELETE FROM profile")
    suspend fun deleteProfile()

    @Query("DELETE FROM balances")
    suspend fun deleteBalances()

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM notifications")
    suspend fun deleteNotifications()

    @Query("DELETE FROM payment_methods")
    suspend fun deletePaymentMethods()

    @Query("DELETE FROM settings")
    suspend fun deleteSettings()

    /** Removes everything (sign-out), in one transaction. */
    @Transaction
    suspend fun clearAll() {
        deleteProfile()
        deleteBalances()
        deleteTransactions()
        deleteNotifications()
        deletePaymentMethods()
        deleteSettings()
        deleteAddresses()
    }

    /** Swaps in a complete new snapshot atomically, so a reader never sees half of one. */
    @Transaction
    suspend fun replaceAll(
        profile: ProfileEntity,
        balances: List<BalanceEntity>,
        transactions: List<TransactionEntity>,
        notifications: List<NotificationEntity>,
        methods: List<PaymentMethodEntity>,
        settings: List<SettingEntity>,
        addresses: List<AddressEntity>,
    ) {
        clearAll()
        insertProfile(profile)
        insertBalances(balances)
        insertTransactions(transactions)
        insertNotifications(notifications)
        insertPaymentMethods(methods)
        insertSettings(settings)
        insertAddresses(addresses)
    }
}

@Database(
    entities = [
        ProfileEntity::class,
        BalanceEntity::class,
        TransactionEntity::class,
        NotificationEntity::class,
        PaymentMethodEntity::class,
        SettingEntity::class,
        AddressEntity::class,
    ],
    // 2: added addresses. As a cache it is rebuilt from the server on upgrade.
    version = 2,
    exportSchema = false,
)
abstract class PayCeylonDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        fun create(context: Context): PayCeylonDatabase =
            Room.databaseBuilder(context.applicationContext, PayCeylonDatabase::class.java, "payceylon.db")
                // Only a cache: on a schema change it is rebuilt from the server.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
