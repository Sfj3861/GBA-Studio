package com.sfj3861.gbastudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PortingScreen(){
    val repository= remember{ProjectRepository(LocalContext.current)}
    var projects by remember{mutableStateOf(repository.list())}
    var selectedId by remember{mutableStateOf(projects.firstOrNull()?.id)}
    var report by remember{mutableStateOf<PortingReport?>(null)}
    var status by remember{mutableStateOf("Selecciona un proyecto para analizar su compatibilidad GBA.")}
    val selected=projects.firstOrNull{it.id==selectedId}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Portabilidad a GBA",style=MaterialTheme.typography.headlineMedium)
        Text("Analizador estático preliminar. Busca dependencias de escritorio y patrones que pueden requerir adaptación; no certifica compatibilidad ni sustituye una compilación.")
        Button(onClick={projects=repository.list();selectedId=projects.firstOrNull()?.id;report=null}){Text("Actualizar proyectos")}
        projects.forEach{project->
            Button(onClick={selectedId=project.id;report=null}){Text(if(project.id==selectedId)"✓ \${project.name}" else project.name)}
        }
        Button(enabled=selected!=null,onClick={
            val project=selected
            if(project!=null){report=runCatching{GbaPortingAnalyzer.analyze(project)}.getOrNull();status=if(report==null)"No se pudo analizar el proyecto." else "Análisis completado."}
        }){Text("Analizar proyecto")}
        report?.let{result->
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("\${result.filesScanned} archivos · \${result.sourceBytes} bytes")
                    Text("\${result.findings.size} observaciones")
                    result.findings.forEach{finding->Text("\${finding.severity} · \${finding.file}: \${finding.message}",color=if(finding.severity=="WARNING")MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)}
                }
            }
        }
        Text(status,color=MaterialTheme.colorScheme.secondary)
    }
}
