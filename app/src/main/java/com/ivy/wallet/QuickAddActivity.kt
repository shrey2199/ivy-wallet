package com.ivy.wallet

import android.os.Bundle
import com.ivy.ui.R
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.design.system.IvyMaterial3Theme
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import com.ivy.widget.transaction.AddTransactionWidget
import com.ivy.widget.transaction.AddTransactionWidgetCompact
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : AppCompatActivity() {

    @Inject
    lateinit var transactionRepo: TransactionRepository

    @Inject
    lateinit var transactionMapper: TransactionMapper

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var accountsAct: AccountsAct

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    lateinit var settingsDao: SettingsDao

    @Inject
    lateinit var sharedPrefs: SharedPrefs

    @Inject
    lateinit var ivyContext: IvyWalletCtx

    private companion object {
        const val EXTRA_ADD_TRANSACTION_TYPE = "add_transaction_type_extra"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawType = intent.getStringExtra(EXTRA_ADD_TRANSACTION_TYPE) ?: "EXPENSE"
        val transactionType = try {
            TransactionType.valueOf(rawType)
        } catch (e: Exception) {
            TransactionType.EXPENSE
        }

        setContent {
            var baseCurrency by remember { mutableStateOf("USD") }
            var isDarkTheme by remember { mutableStateOf(false) }
            var isTrueBlack by remember { mutableStateOf(false) }
            var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
            var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            val systemDark = isSystemInDarkTheme()

            LaunchedEffect(Unit) {
                val settings = settingsDao.findFirstOrNull()
                baseCurrency = settings?.currency ?: "USD"
                isDarkTheme = when (settings?.theme) {
                    Theme.LIGHT -> false
                    Theme.DARK -> true
                    Theme.AMOLED_DARK -> true
                    else -> systemDark
                }
                isTrueBlack = settings?.theme == Theme.AMOLED_DARK
                accounts = accountsAct(Unit)
                categories = categoryRepository.findAll().sortedBy { it.orderNum }
                isLoading = false
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                if (accounts.isEmpty()) {
                    Toast.makeText(this, "Please open the app and create an account first.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    IvyMaterial3Theme(
                        dark = isDarkTheme,
                        isTrueBlack = isTrueBlack
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { finish() },
                            color = Color.Black.copy(alpha = 0.4f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                QuickAddFlowContainer(
                                    transactionType = transactionType,
                                    accounts = accounts,
                                    categories = categories,
                                    baseCurrency = baseCurrency,
                                    onDismiss = { finish() },
                                    onSaveTransaction = { amount, category, account, toAccount, title, desc ->
                                        saveTransaction(
                                            type = transactionType,
                                            amount = amount,
                                            category = category,
                                            account = account,
                                            toAccount = toAccount,
                                            title = title,
                                            desc = desc
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveTransaction(
        type: TransactionType,
        amount: Double,
        category: Category?,
        account: Account,
        toAccount: Account?,
        title: String,
        desc: String
    ) {
        lifecycleScope.launch {
            try {
                val transaction = com.ivy.base.legacy.Transaction(
                    accountId = account.id,
                    type = type,
                    amount = amount.toBigDecimal(),
                    toAccountId = toAccount?.id,
                    toAmount = amount.toBigDecimal(),
                    title = title.takeIf { it.isNotBlank() },
                    description = desc.takeIf { it.isNotBlank() },
                    dateTime = timeProvider.utcNow(),
                    categoryId = category?.id?.value,
                    isSynced = false
                )

                val domainTransaction = transaction.toDomain(transactionMapper)
                if (domainTransaction != null) {
                    transactionRepo.save(domainTransaction)

                    // Save selected account as the last used account
                    sharedPrefs.putString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, account.id.toString())

                    // Refresh widget broadcast
                    WalletBalanceWidgetReceiver.updateBroadcast(this@QuickAddActivity)
                    AddTransactionWidget.updateBroadcast(this@QuickAddActivity)
                    AddTransactionWidgetCompact.updateBroadcast(this@QuickAddActivity)

                    Toast.makeText(
                        this@QuickAddActivity,
                        "${type.name.lowercase().replaceFirstChar { it.uppercase() }} of $amount added successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@QuickAddActivity, "Error saving transaction.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@QuickAddActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }
}

enum class QuickAddStep {
    AMOUNT,
    CATEGORY,
    FROM_ACCOUNT,
    TO_ACCOUNT,
    CONFIRM
}

@Composable
fun QuickAddFlowContainer(
    transactionType: TransactionType,
    accounts: List<Account>,
    categories: List<Category>,
    baseCurrency: String,
    onDismiss: () -> Unit,
    onSaveTransaction: (Double, Category?, Account, Account?, String, String) -> Unit
) {
    var step by remember {
        mutableStateOf(QuickAddStep.AMOUNT)
    }

    var enteredAmountStr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedAccount by remember {
        mutableStateOf<Account?>(null)
    }
    var selectedToAccount by remember { mutableStateOf<Account?>(null) }
    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }

    val sharedPrefs = (androidx.compose.ui.platform.LocalContext.current as QuickAddActivity).sharedPrefs

    // Pre-select default account when accounts are loaded
    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && selectedAccount == null) {
            val lastSelectedId = sharedPrefs.getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, null)?.let {
                try { UUID.fromString(it) } catch (e: Exception) { null }
            }
            val defaultAcc = accounts.find { it.id == lastSelectedId } ?: accounts.firstOrNull()
            selectedAccount = defaultAcc

            if (transactionType == TransactionType.TRANSFER) {
                selectedToAccount = accounts.find { it.id != defaultAcc?.id } ?: accounts.firstOrNull()
            }
        }
    }

    val stepHistory = remember { mutableStateListOf<QuickAddStep>() }

    fun navigateTo(nextStep: QuickAddStep) {
        stepHistory.add(step)
        step = nextStep
    }

    fun navigateBack() {
        if (stepHistory.isNotEmpty()) {
            step = stepHistory.removeLast()
        } else {
            onDismiss()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .padding(16.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row with back and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step != QuickAddStep.AMOUNT) {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Text(
                    text = when (step) {
                        QuickAddStep.AMOUNT -> "Enter Amount"
                        QuickAddStep.CATEGORY -> "Select Category"
                        QuickAddStep.FROM_ACCOUNT -> "Transfer From"
                        QuickAddStep.TO_ACCOUNT -> "Transfer To"
                        QuickAddStep.CONFIRM -> "Confirm Details"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "stepAnimation"
            ) { targetStep ->
                when (targetStep) {
                    QuickAddStep.AMOUNT -> {
                        AmountStep(
                            amountStr = enteredAmountStr,
                            baseCurrency = baseCurrency,
                            transactionType = transactionType,
                            onKeyClick = { key ->
                                enteredAmountStr = handleKeyInput(key, enteredAmountStr)
                            },
                            onNext = {
                                if (transactionType == TransactionType.TRANSFER) {
                                    navigateTo(QuickAddStep.FROM_ACCOUNT)
                                } else {
                                    navigateTo(QuickAddStep.CATEGORY)
                                }
                            }
                        )
                    }
                    QuickAddStep.CATEGORY -> {
                        CategoryGridStep(
                            categories = categories,
                            onCategorySelected = { cat ->
                                selectedCategory = cat
                                navigateTo(QuickAddStep.CONFIRM)
                            }
                        )
                    }
                    QuickAddStep.FROM_ACCOUNT -> {
                        AccountSelectorStep(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            onAccountSelected = { acc ->
                                selectedAccount = acc
                                navigateTo(QuickAddStep.TO_ACCOUNT)
                            }
                        )
                    }
                    QuickAddStep.TO_ACCOUNT -> {
                        AccountSelectorStep(
                            accounts = accounts.filter { it.id != selectedAccount?.id },
                            selectedAccount = selectedToAccount,
                            onAccountSelected = { acc ->
                                selectedToAccount = acc
                                navigateTo(QuickAddStep.CONFIRM)
                            }
                        )
                    }
                    QuickAddStep.CONFIRM -> {
                        ConfirmStep(
                            amount = enteredAmountStr.toDoubleOrNull() ?: 0.0,
                            currency = baseCurrency,
                            category = selectedCategory,
                            account = selectedAccount,
                            toAccount = selectedToAccount,
                            transactionType = transactionType,
                            accounts = accounts,
                            titleText = titleText,
                            onTitleChange = { titleText = it },
                            descriptionText = descriptionText,
                            onDescriptionChange = { descriptionText = it },
                            onAccountChange = { selectedAccount = it },
                            onToAccountChange = { selectedToAccount = it },
                            onSave = {
                                selectedAccount?.let { acc ->
                                    onSaveTransaction(
                                        enteredAmountStr.toDoubleOrNull() ?: 0.0,
                                        selectedCategory,
                                        acc,
                                        selectedToAccount,
                                        titleText,
                                        descriptionText
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun handleKeyInput(key: String, currentAmount: String): String {
    return when (key) {
        "⌫" -> if (currentAmount.isNotEmpty()) currentAmount.dropLast(1) else ""
        "." -> if (!currentAmount.contains(".")) {
            if (currentAmount.isEmpty()) "0." else currentAmount + "."
        } else currentAmount
        else -> {
            val dotIndex = currentAmount.indexOf('.')
            if (dotIndex != -1 && currentAmount.length - dotIndex > 3) {
                currentAmount
            } else if (currentAmount == "0") {
                key
            } else {
                currentAmount + key
            }
        }
    }
}

@Composable
fun AmountStep(
    amountStr: String,
    baseCurrency: String,
    transactionType: TransactionType,
    onKeyClick: (String) -> Unit,
    onNext: () -> Unit
) {
    val displayAmount = if (amountStr.isEmpty()) "0" else amountStr

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$displayAmount $baseCurrency",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = when (transactionType) {
                TransactionType.EXPENSE -> Color(0xFFEF5350)
                TransactionType.INCOME -> Color(0xFF66BB6A)
                TransactionType.TRANSFER -> Color(0xFF42A5F5)
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Simple custom keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in keys) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(6.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onKeyClick(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val amountVal = amountStr.toDoubleOrNull() ?: 0.0
        Button(
            onClick = onNext,
            enabled = amountVal > 0.0,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (transactionType) {
                    TransactionType.EXPENSE -> Color(0xFFEF5350)
                    TransactionType.INCOME -> Color(0xFF66BB6A)
                    TransactionType.TRANSFER -> Color(0xFF42A5F5)
                },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun CategoryGridStep(
    categories: List<Category>,
    onCategorySelected: (Category) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            items(categories) { category ->
                val categoryColor = category.color.value.toComposeColor()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCategorySelected(category) }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(categoryColor, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        ItemIconSDefaultIcon(
                            iconName = category.icon?.id,
                            defaultIcon = R.drawable.ic_custom_category_s,
                            tint = findContrastTextColor(categoryColor),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = category.name.value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSelectorStep(
    accounts: List<Account>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(accounts) { account ->
                val accountColor = account.color.toComposeColor()
                val isSelected = selectedAccount?.id == account.id
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) accountColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onAccountSelected(account) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) findContrastTextColor(accountColor) else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmStep(
    amount: Double,
    currency: String,
    category: Category?,
    account: Account?,
    toAccount: Account?,
    transactionType: TransactionType,
    accounts: List<Account>,
    titleText: String,
    onTitleChange: (String) -> Unit,
    descriptionText: String,
    onDescriptionChange: (String) -> Unit,
    onAccountChange: (Account) -> Unit,
    onToAccountChange: (Account) -> Unit,
    onSave: () -> Unit
) {
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var toAccountDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Summary block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$amount $currency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                if (transactionType != TransactionType.TRANSFER) {
                    category?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Category:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it.name.value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (transactionType == TransactionType.TRANSFER) "From Account:" else "Account:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        Text(
                            text = account?.name ?: "Select",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { accountDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        onAccountChange(acc)
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (transactionType == TransactionType.TRANSFER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To Account:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            Text(
                                text = toAccount?.name ?: "Select",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { toAccountDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = toAccountDropdownExpanded,
                                onDismissRequest = { toAccountDropdownExpanded = false }
                            ) {
                                accounts.filter { it.id != account?.id }.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            onToAccountChange(acc)
                                            toAccountDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title and Description inputs
        OutlinedTextField(
            value = titleText,
            onValueChange = onTitleChange,
            label = { Text("Title / Note (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = descriptionText,
            onValueChange = onDescriptionChange,
            label = { Text("Description (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (transactionType) {
                    TransactionType.EXPENSE -> Color(0xFFEF5350)
                    TransactionType.INCOME -> Color(0xFF66BB6A)
                    TransactionType.TRANSFER -> Color(0xFF42A5F5)
                }
            )
        ) {
            Text("Confirm & Save", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
