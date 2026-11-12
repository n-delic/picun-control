package io.github.picuncontrol

import io.github.picuncontrol.model.Feature
import io.github.picuncontrol.model.Features
import io.github.picuncontrol.model.ModelRepository
import io.github.picuncontrol.protocol.Protocol.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRepositoryTest {
  private val json = """
    [
      {
        "device_id": 11,
        "device_name": "ANC-05max",
        "bluetooth_name": "picun anc-05max",
        "device_model": "0001",
        "device_no": "0003",
        "device_status": 20,
        "category": "Over-ear Noise Cancellation",
        "spatial_audio": 1,
        "voice_switching": 1,
        "is_memory": 1,
        "is_fast": 1,
        "unknown_future_field": "ignored",
        "modules": [
          {"title": "Low latency mode"},
          {"title": "Find it"},
          {"title": "Equalizer"},
          {"title": "Set up"},
          {"title": "Denoise"}
        ]
      },
      {
        "device_id": 71,
        "device_name": "F2",
        "bluetooth_name": "Picun F2",
        "device_model": "01",
        "device_no": "01",
        "device_status": 20,
        "category": "TWS",
        "spatial_audio": 1,
        "voice_switching": 1,
        "is_fast": 1,
        "modules": [
          {"title": "Low latency"},
          {"title": "Find it"},
          {"title": "EQ"},
          {"title": "Setting"},
          {"title": "Noise reduction"}
        ]
      }
    ]
  """.trimIndent()

  private val repository = ModelRepository.parse(json)

  @Test fun findsByProduct() {
    val model = repository.findByProduct("0001", "0003")
    assertNotNull(model)
    assertEquals(11, model!!.id)
    assertEquals(Family.A, model.family)
    assertTrue(model.isDelisted)
  }

  @Test fun findsByBluetoothNameCaseInsensitively() {
    val model = repository.findByBluetoothName("PICUN ANC-05MAX")
    assertNotNull(model)
    assertEquals("ANC-05max", model!!.name)
  }

  @Test fun findsFamilyBModel() {
    val model = repository.findByProduct("01", "01")!!
    assertEquals(Family.B, model.family)
    assertTrue(model.isDelisted)
    assertEquals(Features.filterForFamily(Features.forModel(model), Family.B), Features.forModel(model))
  }

  @Test fun unknownProductIsNull() {
    assertNull(repository.findByProduct("9999", "9999"))
    assertNull(repository.findByBluetoothName("Some other buds"))
  }

  @Test fun anc05MaxFeatureSetComesFromModulesAndFlags() {
    val features = Features.forModel(repository.findByProduct("0001", "0003"))
    assertTrue(Feature.GAME_MODE in features)
    assertTrue(Feature.ANC in features)
    assertTrue(Feature.EQ in features)
    assertTrue(Feature.FIND_MY_BUDS in features)
    assertTrue(Feature.SPATIAL in features)
    assertTrue(Feature.LANGUAGE in features)
    assertTrue(Feature.BATTERY in features)
    assertTrue(Feature.VOLUME in features)
    assertFalse(Feature.HEATING in features)
    assertFalse(Feature.LDAC in features)
  }

  @Test fun unknownModelGetsReadOnlyCommonSet() {
    val a = Features.unknown(Family.A)
    assertTrue(Feature.GAME_MODE in a)
    assertTrue(Feature.ANC in a)
    assertTrue(Feature.EQ in a)
    assertTrue(Feature.BATTERY in a)

    val b = Features.unknown(Family.B)
    assertTrue(Feature.GAME_MODE in b)
    assertFalse(Feature.MIC_MODE in b)
    assertFalse(Feature.HEAD_TRACKING in b)
  }

  @Test fun familyBFilterRemovesAOnlyFeatures() {
    val filtered = Features.filterForFamily(
      setOf(Feature.GAME_MODE, Feature.ANC, Feature.MIC_MODE, Feature.LDAC, Feature.SPATIAL),
      Family.B,
    )
    assertEquals(setOf(Feature.GAME_MODE, Feature.ANC, Feature.SPATIAL), filtered)
  }

  @Test fun badJsonFailsParse() {
    assertTrue(runCatching { ModelRepository.parse("not json") }.isFailure)
  }

  @Test fun bundledCatalogueParsesAndCoversKnownModels() {
    val file = listOf(
      java.io.File("src/main/assets/models.json"),
      java.io.File("app/src/main/assets/models.json"),
    ).firstOrNull { it.exists() }
    assertNotNull("models.json asset not found", file)
    val real = ModelRepository.parse(file!!.readText())
    assertEquals(112, real.models.size)
    assertNotNull(real.findByProduct("0001", "0003")) // ANC-05Max
    assertNotNull(real.findByProduct("01", "01")) // F2, Family B
    assertNotNull(real.findById(132)) // F8 Ultra
    // Every catalogue entry must produce a feature set without throwing.
    real.models.forEach { Features.forModel(it) }
  }
}
