package cu.shalev.app.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.domain.service.TablasExport

/** El mismo desglose que se guarda y exporta, visible antes de aprobar el conteo de un empleado. */
@Composable
fun ResumenConteo(conteo: ConteoCierre) {
    Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Text("Conteo de cierre", style = MaterialTheme.typography.titleSmall)
        conteo.billetes.filterValues { it != 0L }.toSortedMap().forEach { (d, n) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                MiniaturaBilleteCup(d)
                Text("× $n = ${Money.format(Cup(d.toLong() * 100 * n))}", style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium))
            }
        }
        Text("Otro efectivo: ${Money.format(conteo.otroEfectivo)}", style = MaterialTheme.typography.bodyMedium)
        TablasExport.filasConteo(conteo).forEach { fila ->
            Text("${fila[1]} (${fila[2]}): esperado ${fila[3]}, contado ${fila[4]}, diferencia ${fila[5]}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (conteo.aprobadoEn == null) "Pendiente de aprobación de inventario" else "Inventario aprobado por ${conteo.aprobadoPor}", style = MaterialTheme.typography.bodyMedium)
        if (conteo.motivo.isNotBlank()) Text("Motivo: ${conteo.motivo}", style = MaterialTheme.typography.bodyMedium)
    }
}
