package vn.ecohome.pos0210

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream=Color(0xFFF4EFE7); val Ink=Color(0xFF2C211B); val Coffee=Color(0xFF5A4032)
data class Dish(val name:String,var price:Int,val cat:String,var enabled:Boolean=true)
fun money(v:Int)="%,dđ".format(v).replace(',', '.')

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Pos()}}}

@Composable fun Pos(){
 var screen by remember{mutableStateOf("TABLES")}; var table by remember{mutableStateOf("Bàn 01")}
 val menu=remember{mutableStateListOf(Dish("Bún gà",40000,"Ăn sáng"),Dish("Bún gà đùi",55000,"Ăn sáng"),Dish("Cà phê đen",25000,"Cà phê"),Dish("Cà phê sữa",30000,"Cà phê"),Dish("Trà đào",35000,"Trà"),Dish("Sinh tố xoài",40000,"Sinh tố"))}
 val cart=remember{mutableStateMapOf<String,Int>()}
 fun home(){screen="TABLES"}
 BackHandler(screen!="TABLES"){screen=when(screen){"ORDER"->"TABLES";"SENT"->"ORDER";"PAY"->"SENT";else->"TABLES"}}
 MaterialTheme(colorScheme=lightColorScheme(primary=Coffee,background=Cream,surface=Color(0xFFFFFCF7),onSurface=Ink)){Surface(Modifier.fillMaxSize(),color=Cream){Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){when(screen){
  "TABLES"->Tables({table=it;screen="ORDER"},{screen="MENU"})
  "ORDER"->Order(table,menu,cart,{screen="TABLES"},{screen="TABLES"},{screen="SENT"})
  "SENT"->Sent(table,{screen="ORDER"},{screen="PAY"},{screen="TABLES"})
  "PAY"->Pay(table,{screen="SENT"},{home()})
  "MENU"->MenuManager(menu,{screen="TABLES"})
 }}}}
}
@Composable fun Top(title:String,sub:String,onBack:(()->Unit)?=null,onHome:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){if(onBack!=null)TextButton(onBack){Text("‹ BACK",fontWeight=FontWeight.Bold)} else Text("0210",fontSize=27.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));Column(horizontalAlignment=Alignment.End){Text(title,fontWeight=FontWeight.Bold);Text(sub,fontSize=12.sp,color=Coffee)};if(onHome!=null)TextButton(onHome){Text("⌂",fontSize=24.sp)}}}
@Composable fun Tables(open:(String)->Unit,menu:()->Unit){Column{Top("BÁN HÀNG","Chọn bàn");Row(Modifier.padding(horizontal=18.dp)){Text("TRONG NHÀ",fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));TextButton(menu){Text("QUẢN LÝ MENU")}};LazyColumn(Modifier.weight(1f).padding(horizontal=14.dp)){items((1..8).map{"Bàn %02d".format(it)}){x->Card(Modifier.fillMaxWidth().padding(5.dp).clickable{open(x)},shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(20.dp)){Text(x,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("TRỐNG",fontSize=12.sp,color=Coffee)}}}};Button({open("Mang đi")},Modifier.fillMaxWidth().padding(18.dp).height(54.dp)){Text("MANG ĐI")}}}
@Composable fun Order(t:String,menu:List<Dish>,cart:MutableMap<String,Int>,back:()->Unit,home:()->Unit,send:()->Unit){var cat by remember{mutableStateOf("Tất cả")};val cats=listOf("Tất cả","Cà phê","Ăn sáng","Trà","Sinh tố");val count=cart.values.sum();val total=menu.sumOf{it.price*(cart[it.name]?:0)};Column{Top(t,"ORDER MÓN",back,home);Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceEvenly){cats.forEach{Text(it,Modifier.clickable{cat=it}.padding(7.dp),fontWeight=if(cat==it)FontWeight.Black else FontWeight.Normal,color=if(cat==it)Coffee else Ink)}};LazyColumn(Modifier.weight(1f).padding(12.dp)){items(menu.filter{it.enabled&&(cat=="Tất cả"||it.cat==cat)}){d->val q=cart[d.name]?:0;Card(Modifier.fillMaxWidth().padding(5.dp),shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(d.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(money(d.price),color=Coffee)};if(q>0){OutlinedButton({if(q<=1)cart.remove(d.name) else cart[d.name]=q-1},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(42.dp)){Text("−",fontSize=22.sp)};Text("$q",Modifier.width(38.dp),fontSize=18.sp,fontWeight=FontWeight.Black);OutlinedButton({cart[d.name]=q+1},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(42.dp)){Text("+",fontSize=20.sp)}}else Button({cart[d.name]=1},contentPadding=PaddingValues(horizontal=18.dp)){Text("+ THÊM")}}}}};Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){Text("$count món  •  ${money(total)}",fontWeight=FontWeight.Black,fontSize=18.sp);Spacer(Modifier.weight(1f));TextButton({}){Text("XEM GIỎ")}};Button(send,enabled=count>0,modifier=Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=8.dp,bottom=14.dp).height(56.dp)){Text("GỬI LÀM HÀNG",fontWeight=FontWeight.Bold)}}}
@Composable fun Sent(t:String,more:()->Unit,pay:()->Unit,home:()->Unit){Column{Top(t,"ĐÃ GỌI",more,home);Card(Modifier.fillMaxWidth().padding(18.dp),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(20.dp)){Text("ĐƠN VỪA GỬI",fontWeight=FontWeight.Black);Text("Phiếu bếp đang chờ máy in Bluetooth",Modifier.padding(top=8.dp),color=Coffee)}};Spacer(Modifier.weight(1f));Row(Modifier.padding(18.dp)){OutlinedButton(more,Modifier.weight(1f).height(54.dp)){Text("GỌI THÊM")};Spacer(Modifier.width(8.dp));Button(pay,Modifier.weight(1f).height(54.dp)){Text("THANH TOÁN")}}}}
@Composable fun Pay(t:String,back:()->Unit,done:()->Unit){var method by remember{mutableStateOf("TRANSFER")};var printed by remember{mutableStateOf(false)};Column{Top(t,"THANH TOÁN",back,done);Column(Modifier.weight(1f).padding(22.dp)){Text("TỔNG CỘNG",color=Coffee);Text("135.000đ",fontSize=32.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(20.dp));Row{FilterChip(method=="CASH",{method="CASH"},{Text("TIỀN MẶT")});Spacer(Modifier.width(10.dp));FilterChip(method=="TRANSFER",{method="TRANSFER"},{Text("CHUYỂN KHOẢN")})};if(method=="TRANSFER")Card(Modifier.fillMaxWidth().padding(top=18.dp)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("VIETQR",fontSize=28.sp,fontWeight=FontWeight.Black);Text("QR thật sẽ cấu hình sau");Button({printed=true},Modifier.padding(top=12.dp)){Text(if(printed)"ĐÃ IN BILL + QR" else "IN BILL + QR")}}};if(printed&&method=="CASH")Text("Bill QR đã in; doanh thu cuối cùng sẽ ghi nhận TIỀN MẶT.",Modifier.padding(top=12.dp),color=Coffee)};Button(done,Modifier.fillMaxWidth().padding(18.dp).height(58.dp)){Text(if(method=="CASH")"XÁC NHẬN ĐÃ TRẢ TIỀN MẶT" else "XÁC NHẬN ĐÃ NHẬN CHUYỂN KHOẢN",fontWeight=FontWeight.Bold)}}}
@Composable fun MenuManager(menu:MutableList<Dish>,back:()->Unit){var name by remember{mutableStateOf("")};var price by remember{mutableStateOf("")};Column{Top("QUẢN LÝ MENU","V0.3",back,back);LazyColumn(Modifier.weight(1f).padding(horizontal=14.dp)){items(menu){d->Card(Modifier.fillMaxWidth().padding(5.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(d.name,fontWeight=FontWeight.Bold);Text("${d.cat} • ${money(d.price)}")};Switch(d.enabled,{d.enabled=it;val i=menu.indexOf(d);menu[i]=d})}}};item{Card(Modifier.fillMaxWidth().padding(5.dp)){Column(Modifier.padding(16.dp)){Text("THÊM MÓN",fontWeight=FontWeight.Black);OutlinedTextField(name,{name=it},label={Text("Tên món")},modifier=Modifier.fillMaxWidth());OutlinedTextField(price,{price=it.filter(Char::isDigit)},label={Text("Giá bán")},modifier=Modifier.fillMaxWidth());Button({val p=price.toIntOrNull();if(name.isNotBlank()&&p!=null){menu.add(Dish(name,p,"Khác"));name="";price=""}},Modifier.fillMaxWidth().padding(top=10.dp)){Text("THÊM VÀO MENU")}}}}};Text("Có thể bật/tắt món và thêm món mới. Sửa/xóa/sắp xếp + lưu SQLite sẽ triển khai tiếp.",Modifier.padding(18.dp),fontSize=12.sp,color=Coffee)}}