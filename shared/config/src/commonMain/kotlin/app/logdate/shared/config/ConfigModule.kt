package app.logdate.shared.config

import org.koin.core.module.Module
import org.koin.dsl.module

val configModule: Module = module {
    single<LogDateConfigRepository> { 
        DefaultLogDateConfigRepository() 
    }
}