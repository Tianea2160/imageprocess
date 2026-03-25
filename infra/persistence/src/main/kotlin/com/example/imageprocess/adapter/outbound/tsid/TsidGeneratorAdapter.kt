package com.example.imageprocess.adapter.outbound.tsid

import com.example.imageprocess.domain.port.outbound.TsidGenerator
import io.hypersistence.tsid.TSID
import org.springframework.stereotype.Component

@Component
class TsidGeneratorAdapter : TsidGenerator {
    override fun generate(): String = TSID.Factory.getTsid().toString()
}
