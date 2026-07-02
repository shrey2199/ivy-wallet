package com.ivy.wallet

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.design.api.IvyUI
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.system.IvyMaterial3Theme
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.appDesign
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.ui.R
import com.ivy.ui.time.TimeFormatter
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.deprecated.logic.SmartTitleSuggestionsLogic
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.WrapContentRow
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import com.ivy.widget.transaction.AddTransactionWidget
import com.ivy.widget.transaction.AddTransactionWidgetCompact
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class QuickAddStep {
    AMOUNT,
    CATEGORY,
    FROM_ACCOUNT,
    TO_ACCOUNT,
    CONFIRM
}

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

    @Inject
    lateinit var timeConverter: TimeConverter

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var smartTitleSuggestionsLogic: SmartTitleSuggestionsLogic

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
                val theme = settings?.theme ?: if (systemDark) Theme.DARK else Theme.LIGHT
                ivyContext.switchTheme(theme)

                baseCurrency = settings?.currency ?: "USD"
                isDarkTheme = when (theme) {
                    Theme.LIGHT -> false
                    Theme.DARK -> true
                    Theme.AMOLED_DARK -> true
                    else -> systemDark
                }
                isTrueBlack = theme == Theme.AMOLED_DARK
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
                        IvyUI(
                            design = appDesign(ivyContext),
                            includeSurface = false,
                            timeConverter = timeConverter,
                            timeProvider = timeProvider,
                            timeFormatter = timeFormatter
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
                                        timeFormatter = timeFormatter,
                                        smartTitleSuggestionsLogic = smartTitleSuggestionsLogic,
                                        onDismiss = { finish() },
                                        onSaveTransaction = { amount, category, account, toAccount, title, desc, dateTime ->
                                            saveTransaction(
                                                type = transactionType,
                                                amount = amount,
                                                category = category,
                                                account = account,
                                                toAccount = toAccount,
                                                title = title,
                                                desc = desc,
                                                dateTime = dateTime
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
    }

    private fun saveTransaction(
        type: TransactionType,
        amount: Double,
        category: Category?,
        account: Account,
        toAccount: Account?,
        title: String,
        desc: String,
        dateTime: Instant
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
                    dateTime = dateTime,
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

@Composable
fun QuickAddFlowContainer(
    transactionType: TransactionType,
    accounts: List<Account>,
    categories: List<Category>,
    baseCurrency: String,
    timeFormatter: TimeFormatter,
    smartTitleSuggestionsLogic: SmartTitleSuggestionsLogic,
    onDismiss: () -> Unit,
    onSaveTransaction: (Double, Category?, Account, Account?, String, String, Instant) -> Unit
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
    var selectedDateTime by remember { mutableStateOf(Instant.now()) }

    val sharedPrefs = (LocalContext.current as QuickAddActivity).sharedPrefs

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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = UI.colors.pure
        ),
        border = BorderStroke(1.dp, UI.colors.medium.copy(alpha = 0.5f)),
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
                            tint = UI.colors.pureInverse
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
                    style = UI.typo.h2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold)
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = UI.colors.pureInverse
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
                            selectedDateTime = selectedDateTime,
                            onDateTimeChange = { selectedDateTime = it },
                            timeFormatter = timeFormatter,
                            smartTitleSuggestionsLogic = smartTitleSuggestionsLogic,
                            onSave = {
                                selectedAccount?.let { acc ->
                                    onSaveTransaction(
                                        enteredAmountStr.toDoubleOrNull() ?: 0.0,
                                        selectedCategory,
                                        acc,
                                        selectedToAccount,
                                        titleText,
                                        descriptionText,
                                        selectedDateTime
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
            style = UI.typo.nH1.style(
                color = when (transactionType) {
                    TransactionType.EXPENSE -> Color(0xFFEF5350)
                    TransactionType.INCOME -> Color(0xFF66BB6A)
                    TransactionType.TRANSFER -> Color(0xFF42A5F5)
                },
                fontWeight = FontWeight.Bold
            ),
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
                                .background(UI.colors.medium.copy(alpha = 0.15f))
                                .clickable { onKeyClick(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                style = UI.typo.h2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold)
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
            shape = UI.shapes.rFull,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (transactionType) {
                    TransactionType.EXPENSE -> Color(0xFFEF5350)
                    TransactionType.INCOME -> Color(0xFF66BB6A)
                    TransactionType.TRANSFER -> Color(0xFF42A5F5)
                },
                disabledContainerColor = UI.colors.medium.copy(alpha = 0.3f)
            )
        ) {
            Text("Next", style = UI.typo.b1.style(color = Color.White, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun CategoryGridStep(
    categories: List<Category>,
    onCategorySelected: (Category) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WrapContentRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 4.dp),
            items = categories,
            verticalMarginBetweenRows = 10.dp,
            horizontalMarginBetweenItems = 10.dp
        ) { category ->
            val categoryColor = category.color.value.toComposeColor()
            Row(
                modifier = Modifier
                    .clip(UI.shapes.rFull)
                    .background(categoryColor.copy(alpha = 0.15f))
                    .border(1.5.dp, categoryColor, UI.shapes.rFull)
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(categoryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ItemIconSDefaultIcon(
                        iconName = category.icon?.id,
                        defaultIcon = R.drawable.ic_custom_category_s,
                        tint = findContrastTextColor(categoryColor),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = category.name.value,
                    style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.SemiBold)
                )
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
                        .clip(UI.shapes.rFull)
                        .background(
                            if (isSelected) accountColor else UI.colors.medium.copy(alpha = 0.15f)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) UI.colors.pureInverse else Color.Transparent,
                            shape = UI.shapes.rFull
                        )
                        .clickable { onAccountSelected(account) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.name,
                        style = UI.typo.b1.style(
                            color = if (isSelected) findContrastTextColor(accountColor) else UI.colors.pureInverse,
                            fontWeight = FontWeight.Bold
                        ),
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
    selectedDateTime: Instant,
    onDateTimeChange: (Instant) -> Unit,
    timeFormatter: TimeFormatter,
    smartTitleSuggestionsLogic: SmartTitleSuggestionsLogic,
    onAccountChange: (Account) -> Unit,
    onToAccountChange: (Account) -> Unit,
    onSave: () -> Unit
) {
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var toAccountDropdownExpanded by remember { mutableStateOf(false) }
    var titleSuggestions by remember { mutableStateOf<Set<String>>(emptySet()) }

    val context = LocalContext.current
    val localDateTime = LocalDateTime.ofInstant(selectedDateTime, ZoneId.systemDefault())

    LaunchedEffect(titleText, category, account) {
        if (titleText.isNotEmpty()) {
            kotlinx.coroutines.delay(250) // Debounce typing to prevent heavy database queries on every keystroke
        }
        titleSuggestions = smartTitleSuggestionsLogic.suggest(
            title = titleText,
            categoryId = category?.id?.value,
            accountId = account?.id
        )
    }

    val formattedDateTime = with(timeFormatter) {
        selectedDateTime.formatLocal(TimeFormatter.Style.DateAndTime(includeWeekDay = true))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Summary block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = UI.colors.medium.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, UI.colors.medium.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount:", style = UI.typo.b2.style(color = UI.colors.mediumInverse))
                    Text("$amount $currency", style = UI.typo.nB1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
                }

                if (transactionType != TransactionType.TRANSFER) {
                    category?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Category:", style = UI.typo.b2.style(color = UI.colors.mediumInverse))
                            Text(it.name.value, style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
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
                        style = UI.typo.b2.style(color = UI.colors.mediumInverse)
                    )
                    Box {
                        Text(
                            text = account?.name ?: "Select",
                            style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(UI.colors.medium.copy(alpha = 0.2f))
                                .clickable { accountDropdownExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
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
                        Text("To Account:", style = UI.typo.b2.style(color = UI.colors.mediumInverse))
                        Box {
                            Text(
                                text = toAccount?.name ?: "Select",
                                style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(UI.colors.medium.copy(alpha = 0.2f))
                                    .clickable { toAccountDropdownExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Date & Time Picker trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date & Time:", style = UI.typo.b2.style(color = UI.colors.mediumInverse))
                    Text(
                        text = formattedDateTime,
                        style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(UI.colors.medium.copy(alpha = 0.2f))
                            .clickable {
                                // Show date picker first
                                val datePickerDialog = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        // Show time picker next
                                        val timePickerDialog = TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val newDateTime = LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                                                onDateTimeChange(newDateTime.atZone(ZoneId.systemDefault()).toInstant())
                                            },
                                            localDateTime.hour,
                                            localDateTime.minute,
                                            true
                                        )
                                        timePickerDialog.show()
                                    },
                                    localDateTime.year,
                                    localDateTime.monthValue - 1,
                                    localDateTime.dayOfMonth
                                )
                                datePickerDialog.show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title and Description inputs
        OutlinedTextField(
            value = titleText,
            onValueChange = onTitleChange,
            label = { Text("Title / Note (Optional)", style = UI.typo.b2.style(color = UI.colors.mediumInverse)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UI.colors.pureInverse,
                unfocusedBorderColor = UI.colors.medium,
                focusedTextColor = UI.colors.pureInverse,
                unfocusedTextColor = UI.colors.pureInverse
            ),
            singleLine = true
        )

        // Suggestion list
        if (titleSuggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(titleSuggestions.toList()) { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(UI.shapes.rFull)
                            .background(UI.colors.medium.copy(alpha = 0.15f))
                            .border(1.dp, UI.colors.medium, UI.shapes.rFull)
                            .clickable { onTitleChange(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestion,
                            style = UI.typo.b2.style(color = UI.colors.pureInverse).copy(fontSize = 13.sp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = descriptionText,
            onValueChange = onDescriptionChange,
            label = { Text("Description (Optional)", style = UI.typo.b2.style(color = UI.colors.mediumInverse)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UI.colors.pureInverse,
                unfocusedBorderColor = UI.colors.medium,
                focusedTextColor = UI.colors.pureInverse,
                unfocusedTextColor = UI.colors.pureInverse
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = UI.shapes.rFull,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (transactionType) {
                    TransactionType.EXPENSE -> Color(0xFFEF5350)
                    TransactionType.INCOME -> Color(0xFF66BB6A)
                    TransactionType.TRANSFER -> Color(0xFF42A5F5)
                }
            )
        ) {
            Text("Confirm & Save", style = UI.typo.b1.style(color = Color.White, fontWeight = FontWeight.Bold))
        }
    }
}
