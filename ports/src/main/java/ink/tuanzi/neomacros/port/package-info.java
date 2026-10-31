/**
 * Version-independent SPI: port interfaces ({@code WorldPort},
 * {@code ChatPort}, ...), the {@code NeomacrosAdapter} aggregate factory, and
 * {@link ink.tuanzi.neomacros.port.UnsupportedPortFeature}; neutral value
 * types live in {@code ink.tuanzi.neomacros.port.model}.
 *
 * <p>Like {@code core}, this package must not import {@code net.minecraft.*}
 * or Fabric APIs; the dependency guard test enforces it at build time. Ports
 * make no thread-safety guarantees of their own — implementations that need
 * the game main thread say so, and core marshals calls (runOnMain) per the
 * threading model.</p>
 *
 * <p><strong>Evolution rule:</strong> port interfaces only ever grow. New
 * methods must carry {@code default} bodies that either degrade gracefully or
 * throw {@link ink.tuanzi.neomacros.port.UnsupportedPortFeature}, letting
 * older milestone nodes keep compiling until their adapters catch up; existing
 * signatures never change, and deprecation runs for two node cycles before
 * removal.</p>
 *
 * <p>Sink-registration methods (event sources) are invoked by the adapter's
 * game callbacks; core registers publishing sinks and forwards into the event
 * bus, so adapters stay free of event-framework knowledge.</p>
 */
package ink.tuanzi.neomacros.port;
