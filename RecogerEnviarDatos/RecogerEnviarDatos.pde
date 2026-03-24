

 
// Put your libraries here (#include ...)
#include <WaspSensorEvent_v30.h>
#include <WaspXBee802.h>
#include <WaspFrame.h>


uint8_t status;
int x_acc;
int y_acc;
int z_acc;
float temp;
float humd;
float pres;
float value;
uint8_t val = 0;
pirSensorClass pir(SOCKET_1);
char RX_ADDRESS[] = "0013A20041951872";
char WASPMOTE_ID[] = "node_01";
uint8_t error;



void setup()
{ 

USB.ON();
ACC.ON();
ACC.setFF(); 

RTC.ON();
RTC.setTime("26:02:19:04:12:51:00");

RTC.setAlarm1(0,0,0,30,RTC_OFFSET,RTC_ALM1_MODE2);
Events.ON();


 val = pir.readPirSensor();
  while (val == 1)
  {
    USB.println(F("...wait for PIR stabilization"));
    delay(1000);
    val = pir.readPirSensor();    
  }
  Events.attachInt();

frame.setID( WASPMOTE_ID );

}

void loop()
{
 // Read the ACC value and write it to the USB. Add time and date.
  status = ACC.check();

  //----------X Value-----------------------
  x_acc = ACC.getX();

  //----------Y Value-----------------------
  y_acc = ACC.getY();

  //----------Z Value-----------------------
  z_acc = ACC.getZ();

  //-------------------------------
 USB.println(status, HEX);
  USB.println(F("\n \t0X\t0Y\t0Z")); 
  USB.print(F(" ACC\t")); 
  USB.print(x_acc, DEC);
  USB.print(F("\t")); 
  USB.print(y_acc, DEC);
  USB.print(F("\t")); 
  USB.println(z_acc, DEC);
  USB.println(RTC.getTime()); 

  //Enter sleep mode 
USB.println(F("Waspmote goes to sleep..."));
PWR.sleep(ALL_OFF);
USB.ON();
ACC.ON();
ACC.unsetFF(); 
USB.println(F("Waspmote wakes up!"));

//If a Free-Fall was detected → write a message
if( intFlag & ACC_INT )
  {
    // clear interruption flag
    intFlag &= ~(ACC_INT);
    
    // print info
    USB.ON();
    USB.println(F("++++++++++++++++++++++++++++"));
    USB.println(F("++ ACC interrupt detected ++"));
    USB.println(F("++++++++++++++++++++++++++++")); 
    USB.println(); 

    
    Utils.setLED(LED0, LED_ON);
    xbee802.ON();
  
  ///////////////////////////////////////////
  // 1. Create ASCII frame
  ///////////////////////////////////////////  

  // create new frame
 frame.createFrame(ASCII);  
  
  // add frame fields
 frame.addSensor(SENSOR_STR, (char*) "ACC interrupt detected"); 

  

  ///////////////////////////////////////////
  // 2. Send packet
  ///////////////////////////////////////////  

  // send XBee packet
  error = xbee802.send( RX_ADDRESS, frame.buffer, frame.length );   
  
  // check TX flag
  if( error == 0 )
  {
    USB.println(F("send ok"));

  }
  else 
  {
    USB.println(F("send error"));
  
  }
  Utils.setLED(LED0, LED_OFF);
  }

//If a RTC alarm was detected → read sensors, write a message

if( intFlag & RTC_INT )
  {
    //Set up a RTC alarm with an offset of 30 seconds      
    RTC.setAlarm1(0,0,0,30,RTC_OFFSET,RTC_ALM1_MODE2);
    Events.ON();
    // clear interruption flag
    intFlag &= ~(RTC_INT);
    Utils.setLED(LED1, LED_ON);
    
    USB.println(F("-------------------------"));
    USB.println(F("RTC INT Captured"));
    USB.println(F("-------------------------"));


  temp = Events.getTemperature();

  humd = Events.getHumidity();

  pres = Events.getPressure(); 
   USB.println("-----------------------------");
  USB.print("Temperature: ");
  USB.printFloat(temp, 2);
  USB.println(F(" Celsius"));
  USB.print("Humidity: ");
  USB.printFloat(humd, 1); 
  USB.println(F(" %")); 
  USB.print("Pressure: ");
  USB.printFloat(pres, 2); 
  USB.println(F(" Pa")); 
  USB.println("-----------------------------");  
  Utils.setLED(LED1, LED_OFF);

  Utils.setLED(LED0, LED_ON);
  xbee802.ON();
  
  ///////////////////////////////////////////
  // 1. Create ASCII frame
  ///////////////////////////////////////////  

  // create new frame
  frame.createFrame(ASCII);  
  
  // add frame fields
   frame.addSensor(SENSOR_IN_TEMP, temp); 
   frame.addSensor(SENSOR_STR, (char*)" Celsius"); 
   frame.addSensor(SENSOR_EVENTS_HUM, humd);
   frame.addSensor(SENSOR_STR, (char*)" %"); 
   frame.addSensor(SENSOR_EVENTS_PRES, pres); 
   frame.addSensor(SENSOR_STR, (char*)" Pa");
  

  ///////////////////////////////////////////
  // 2. Send packet
  ///////////////////////////////////////////  

  // send XBee packet
  error = xbee802.send( RX_ADDRESS, frame.buffer, frame.length );   
  
  // check TX flag
  if( error == 0 )
  {
    USB.println(F("send ok"));

  }
  else 
  {
    USB.println(F("send error"));
  
  }
    Utils.setLED(LED0, LED_OFF);
  }

//If presence is detected, write a messsage
 if (intFlag & SENS_INT)
  {
    // Disable interruptions from the board
    Events.detachInt();
    
    // Load the interruption flag
    Events.loadInt();
    
    // In case the interruption came from PIR
    if (pir.getInt())
    {
      USB.println(F("-----------------------------"));
      USB.println(F("Interruption from PIR"));
      USB.println(F("-----------------------------"));
    }  
    
    Utils.setLED(LED0, LED_ON);
    xbee802.ON();
  
  ///////////////////////////////////////////
  // 1. Create ASCII frame
  ///////////////////////////////////////////  

  // create new frame
  frame.createFrame(ASCII);  
  
  // add frame fields
  frame.addSensor(SENSOR_STR, (char*) "Interruption from PIR"); 

  

  ///////////////////////////////////////////
  // 2. Send packet
  ///////////////////////////////////////////  

  // send XBee packet
  error = xbee802.send( RX_ADDRESS, frame.buffer, frame.length );   
  
  // check TX flag
  if( error == 0 )
  {
    USB.println(F("send ok"));

  }
  else 
  {
    USB.println(F("send error"));
  
  }
  Utils.setLED(LED0, LED_OFF);
  }





USB.ON();
ACC.ON();
ACC.setFF();

Events.ON();



 

}
