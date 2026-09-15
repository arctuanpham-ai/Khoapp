package vn.ecohome.pos0210

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TableCardResponsiveTest {
 @get:Rule val compose=createComposeRule()
 private fun cards(count:Int)=List(count){i->when(i){
  0->TableCardUi("t1","Bàn 01","Trong nhà",true,101,0,ServiceTimerPresentation(38,4,"đang làm",ServiceAgeBand.OVERDUE))
  1->TableCardUi("t2","Bàn 02","Ngoài trời",true,102,1,ServiceTimerPresentation(22,null,"đang làm",ServiceAgeBand.ORANGE))
  else->TableCardUi("t${i+1}","Bàn ${(i+1).toString().padStart(2,'0')}",if(i%2==0)"Trong nhà" else "Ngoài trời",false)
 }}
 private fun verify(count:Int){
  compose.setContent{MaterialTheme{ResponsiveTableGrid(cards(count),Modifier.width(360.dp).height(600.dp).testTag("fixture"))}}
  compose.onNodeWithTag("table-name-t1").assertIsDisplayed();compose.onNodeWithTag("table-priority-t1").assertIsDisplayed()
  compose.onNodeWithTag("table-priority-t2").assertIsDisplayed();compose.onNodeWithTag("table-timer-t1").assertIsDisplayed();compose.onNodeWithTag("table-addon-t1").assertIsDisplayed()
  val card:Rect=compose.onNodeWithTag("table-card-t1").fetchSemanticsNode().boundsInRoot
  val addon:Rect=compose.onNodeWithTag("table-addon-t1").fetchSemanticsNode().boundsInRoot
  assertTrue("timer phụ bị clip ở $count bàn",addon.bottom<=card.bottom+0.5f)
  compose.onNodeWithTag("table-grid").performScrollToNode(hasTestTag("table-name-t$count"));compose.onNodeWithTag("table-name-t$count").assertIsDisplayed()
 }
 @Test fun fourTablesRenderWithoutClipping()=verify(4)
 @Test fun twelveTablesRenderWithoutClipping()=verify(12)
 @Test fun sixteenTablesRenderWithoutClipping()=verify(16)
 @Test fun twentyFourTablesScrollWithoutClipping()=verify(24)
}
