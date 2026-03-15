#you need to measure the full volume of bottle
#this implementation is only if there is no water yet in bottle
#if you want you can change the parameter volume_bottle to massWaterFullBottle
def calculateWater(totalVolumebottle,tempHotTank,tempColdTank,desiredTemp):#this is when we assume there is no water yet in the bottle (so no starting temp in bottle)
    massWaterFullbottle=totalVolumebottle*10    # f.e 70 cl is 700g 
    if not tempColdTank<=desiredTemp<=tempHotTank: 
       print("Error, choose a temperature between "+str(tempColdTank)+"°C and "+ str(tempHotTank)+"°C" )     
       return None
          #formula is derived from m_cold*C_water*T_cold+m_hot*C_water*T_hot=(m_cold+mhot)*C_water*T_desired C is constant so you can drop it 
         
    mass_cold= massWaterFullbottle*(tempHotTank-desiredTemp)/(tempHotTank-tempColdTank)   


    return mass_cold      

#this is when there is already water in bottle

#if the bottle contains a lot of warm water and desired temp is under that, it needs a lot of cold water (so it could go over max level), i added a check for this so it cannot exceed
#same logic if bottle contains a lot of cold water
def calculateWaterBottleFilled(volumeWaterAlrInBottle,totalVolumeBottle,tempHotTank,tempInBottle,tempColdTank,desiredTemp):
   massWaterFullBottle=totalVolumeBottle*10
   massAlrInBottle=volumeWaterAlrInBottle*10
   if not tempColdTank<=desiredTemp<=tempHotTank: 
       print("Error, choose a temperature between "+str(tempColdTank)+"°C and "+ str(tempHotTank)+"°C" )     
       return 
   if massAlrInBottle == massWaterFullBottle:      
       print("Bottle is already full")
       return None
   mass_cold=(massWaterFullBottle*(desiredTemp-tempHotTank)+massAlrInBottle*(tempHotTank-tempInBottle))/(tempColdTank-tempHotTank)
   if mass_cold<0:   #this is a check if there is too much cold water in bottle so that you can never reach the higher temp within the volume of the bottle
       print("Desired Temp too high for current volume and temp in bottle")
       return None
   if mass_cold > massWaterFullBottle-massAlrInBottle:   #this is a check to see if there is too much hot water so that you can never reach the lower temp within the volume of the bottle
       print("Desired temperature too low For current volume and temp in bottle")
       return None
   
   return mass_cold
                              