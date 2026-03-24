#include <WaspXBee802.h>
#include <WaspFrame.h>

uint8_t error;

void setup() {  
  USB.ON();
  USB.println(F("Receptor de datos iniciado"));
  xbee802.ON();
}

void loop() { 
   error=xbee802.receivePacketTimeout(50000);

  if (error == 0) {
    USB.println(F("\n--- PAQUETE DETECTADO ---"));
    char* payload_str = (char*)xbee802._payload;
    
    char* p = strtok(payload_str, "#");
    int contador_campo = 0;

    while (p != NULL) {
      contador_campo++;

      //MAC (Campo 2)
      if (contador_campo == 2) {
        USB.print(F("MAC Origen: "));
        USB.println(p);
      }
      //ID del Nodo (Campo 3)
      else if (contador_campo == 3) {
        USB.print(F("ID Nodo: "));
        USB.println(p);
      }
      //Nº de Mensaje (Campo 4)
      else if (contador_campo == 4) {
        USB.print(F("Mensaje Nº: "));
        USB.println(p);
        USB.println(F("--- Lecturas ---"));
      }
      else if (contador_campo > 4) {
        if (contador_campo % 2 != 0) {
          USB.print(p);  
        } 
        else {
          USB.println(p); 
        }
      }
      
      p = strtok(NULL, "#");
    
    USB.println(F("-----------------------"));
    }
  }
}

