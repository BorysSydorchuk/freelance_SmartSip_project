#you need to measure the full volume of bottle
#this implementation is only if there is no water yet in bottle
def calculateWater(volume_bottle,tempHotTank,tempColdTank,desiredTemp):#this is when we assume there is no water yet in the bottle (so no starting temp in bottle)
    massWaterFullbottle=volume_bottle*10    # f.e 70 cl is 700g 
    if not tempColdTank<=desiredTemp<=tempHotTank: 
       print("Error, choose a temperature between"+str(tempColdTank)+"°C and "+ str(tempHotTank)+"°C" )     
       return None
          #formula is derived from m_cold*C_water*T_cold+m_hot*C_water*T_hot=(m_cold+mhot)*C_water*T_desired C is constant so you can drop it 
         
    mass_cold= massWaterFullbottle*(tempHotTank-desiredTemp)/(tempHotTank-tempColdTank)   


    return mass_cold                                