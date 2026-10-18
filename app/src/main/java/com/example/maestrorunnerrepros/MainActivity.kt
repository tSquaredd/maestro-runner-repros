package com.example.maestrorunnerrepros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.maestrorunnerrepros.theme.MaestroRunnerReprosTheme

class MainActivity : ComponentActivity() {
  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MaestroRunnerReprosTheme {
        // Test tags become resource ids, so the flows can match them with `id:`.
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
          var screen by rememberSaveable { mutableStateOf("home") }
          when (screen) {
            "home" -> HomeScreen(onOpen = { screen = it })
            "column" -> ScrollReproScreen(lazy = false)
            "lazy" -> ScrollReproScreen(lazy = true)
            else -> SheetReproScreen()
          }
        }
      }
    }
  }
}

/** The screen `clear-state.yaml` waits for after every `launchApp: clearState`. */
@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
  Column(
    Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Maestro runner repros", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("home.title"))
    Button(onClick = { onOpen("sheet") }, modifier = Modifier.testTag("home.openSheetRepro")) { Text("Scroll repro: bottom sheet") }
    Button(onClick = { onOpen("lazy") }, modifier = Modifier.testTag("home.openLazyRepro")) { Text("Scroll repro: LazyColumn") }
    Button(onClick = { onOpen("column") }, modifier = Modifier.testTag("home.openColumnRepro")) { Text("Scroll repro: Column") }
  }
}

/**
 * A list that ends above a bottom bar, like a bottom sheet above the gesture bar.
 *
 * The list's last row, Delete, starts 40 dp above the list's bottom edge and runs 80 dp past it, behind
 * the bar. Measured against the screen the whole row is visible; inside the list only a third of it
 * shows. A tap on its centre lands on the bar, which counts the taps it gets.
 */
@Composable
private fun ScrollReproScreen(lazy: Boolean) {
  var barTaps by rememberSaveable { mutableIntStateOf(0) }
  var deleted by rememberSaveable { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
      val listHeight = maxHeight
      if (lazy) {
        LazyColumn(Modifier.fillMaxSize().testTag("repro.list").padding(horizontal = 24.dp)) {
          item { Spacer(Modifier.height(listHeight - 40.dp)) }
          item { DeleteButton { deleted = true } }
          item { Spacer(Modifier.height(24.dp)) }
        }
      } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("repro.list").padding(horizontal = 24.dp)) {
          Spacer(Modifier.height(listHeight - 40.dp))
          DeleteButton { deleted = true }
          Spacer(Modifier.height(24.dp))
        }
      }
    }
    Box(
      Modifier
        .fillMaxWidth()
        .background(Color(0xFF37474F))
        .clickable { barTaps++ }
        .testTag("repro.bottomBar")
        .navigationBarsPadding()
        .height(120.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text("Bottom bar · taps: $barTaps", color = Color.White, modifier = Modifier.testTag("repro.bottomBarTaps"))
    }
  }

  if (deleted) DeletedDialog { deleted = false }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(120.dp).testTag("repro.deleteButton")) { Text("Delete") }
}

/** A dialog is its own window, so it sets testTagsAsResourceId again. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DeletedDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Box(Modifier.semantics { testTagsAsResourceId = true }) { Text("Deleted", Modifier.testTag("repro.deletedDialog")) } },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
  )
}

/**
 * A menu in a bottom sheet: a LazyColumn kept clear of the navigation bar with navigationBarsPadding(),
 * a common way to build one.
 *
 * The list's last row, Delete, starts just above the gesture bar, with only its top edge showing.
 * Inside the sheet the hierarchy reports the row running past the list, to the bottom of the screen,
 * so measured against the screen the whole row is visible. Its centre is on the gesture bar, where a
 * tap goes to the system, not the app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SheetReproScreen() {
  var deleted by rememberSaveable { mutableStateOf(false) }
  Box(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) { Text("Behind the sheet") }
  ModalBottomSheet(
    onDismissRequest = {},
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragHandle = null,
  ) {
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }) {
      // The list's height: the sheet's, less the navigation bar it keeps clear of.
      val listHeight = maxHeight - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
      LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).testTag("repro.list")) {
        item { Text("Menu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.height(listHeight + 8.dp)) }
        item { DeleteButton { deleted = true } }
        item { Spacer(Modifier.height(24.dp)) }
      }
    }
  }
  if (deleted) DeletedDialog { deleted = false }
}
