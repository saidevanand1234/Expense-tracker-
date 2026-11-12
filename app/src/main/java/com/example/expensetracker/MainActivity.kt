
package com.example.expensetracker

import android.os.Bundle
import android.net.Uri
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.Executors

data class Expense(
    val title: String,
    val amount: Double,
    val category: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var etForeignAmount: EditText

    private lateinit var spCategory: Spinner
    private lateinit var spCurrency: Spinner

    private lateinit var tvTotal: TextView
    private lateinit var tvAnalysis: TextView
    private lateinit var tvResult: TextView

    private val expenses = mutableListOf<Expense>()

    private val executor = Executors.newSingleThreadExecutor()

    // Select receipt image from gallery
    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri?.let {
                scanReceipt(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Connect XML views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        etForeignAmount = findViewById(R.id.etForeignAmount)

        spCategory = findViewById(R.id.spCategory)
        spCurrency = findViewById(R.id.spCurrency)

        tvTotal = findViewById(R.id.tvTotal)
        tvAnalysis = findViewById(R.id.tvAnalysis)
        tvResult = findViewById(R.id.tvResult)

        // Category dropdown
        spCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Food", "Travel", "Shopping", "Bills", "Other")
        )

        // Currency dropdown
        spCurrency.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("USD", "EUR", "GBP", "AED", "JPY")
        )

        // Button actions
        findViewById<Button>(R.id.btnRegister)
            .setOnClickListener { registerUser() }

        findViewById<Button>(R.id.btnLogin)
            .setOnClickListener { loginUser() }

        findViewById<Button>(R.id.btnAdd)
            .setOnClickListener { addExpense() }

        findViewById<Button>(R.id.btnScan)
            .setOnClickListener {
                imagePicker.launch("image/*")
            }

        findViewById<Button>(R.id.btnConvert)
            .setOnClickListener { convertCurrency() }
    }

    // 1. Firebase Registration
    private fun registerUser() {

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password",
                Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    Toast.makeText(this, "Registration Successful",
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Registration Failed",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 2. Firebase Login
    private fun loginUser() {

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password",
                Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Successful",
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Login Failed",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 3. Add Expense
    private fun addExpense() {

        val title = etTitle.text.toString().trim()
        val amount = etAmount.text.toString().toDoubleOrNull()

        if (title.isEmpty() || amount == null || amount <= 0) {
            Toast.makeText(this, "Enter valid expense details",
                Toast.LENGTH_SHORT).show()
            return
        }

        val category = spCategory.selectedItem.toString()

        expenses.add(Expense(title, amount, category))

        updateAnalysis()

        etTitle.text.clear()
        etAmount.text.clear()

        Toast.makeText(this, "Expense Added",
            Toast.LENGTH_SHORT).show()
    }

    // 4. Category-wise Analysis
    private fun updateAnalysis() {

        val total = expenses.sumOf { it.amount }

        tvTotal.text = "Total Expenses: Rs. %.2f".format(
            Locale.US, total
        )

        val categoryTotals = expenses.groupBy { it.category }
            .mapValues { entry ->
                entry.value.sumOf { it.amount }
            }

        val result = StringBuilder("Category Analysis\n")

        categoryTotals.forEach { (category, amount) ->

            result.append(
                "$category : Rs. ${
                    String.format(Locale.US, "%.2f", amount)
                }\n"
            )
        }

        tvAnalysis.text = result.toString()
    }

    // 5. Receipt Scanning using ML Kit
    private fun scanReceipt(uri: Uri) {

        try {
            val image = InputImage.fromFilePath(this, uri)

            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->

                    val text = visionText.text

                    val regex = Regex(
                        """(?:₹|Rs\.?|INR\s*)?\s*(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?)(?!\d)""",
                        RegexOption.IGNORE_CASE
                    )

                    val amounts = regex.findAll(text)
                        .mapNotNull {
                            it.groupValues[1]
                                .replace(",", "")
                                .toDoubleOrNull()
                        }
                        .filter { it > 0 }
                        .toList()

                    if (amounts.isNotEmpty()) {

                        val amount = amounts.maxOrNull()!!

                        etAmount.setText(amount.toString())

                        Toast.makeText(this,
                            "Receipt amount detected",
                            Toast.LENGTH_SHORT).show()

                    } else {
                        Toast.makeText(this, "Amount not found",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Text recognition failed",
                        Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    recognizer.close()
                }

        } catch (e: Exception) {

            Toast.makeText(this, "Unable to read receipt",
                Toast.LENGTH_SHORT).show()
        }
    }

    // 6. Foreign Currency to INR
    private fun convertCurrency() {

        val amount = etForeignAmount.text.toString()
            .toDoubleOrNull()

        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Enter valid amount",
                Toast.LENGTH_SHORT).show()
            return
        }

        val currency = spCurrency.selectedItem.toString()

        tvResult.text = "Converting..."

        executor.execute {

            var connection: HttpURLConnection? = null

            try {
                val apiUrl =
                    "https://api.frankfurter.app/latest?from=$currency&to=INR"

                connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"

                val response = connection.inputStream
                    .bufferedReader().use { it.readText() }

                val json = JSONObject(response)

                val rate = json.getJSONObject("rates")
                    .getDouble("INR")

                val convertedAmount = amount * rate

                runOnUiThread {
                    tvResult.text =
                        "$amount $currency = Rs. ${
                            String.format(Locale.US, "%.2f",
                                convertedAmount)
                        }"
                }

            } catch (e: Exception) {

                runOnUiThread {
                    tvResult.text = "Conversion Failed"
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
