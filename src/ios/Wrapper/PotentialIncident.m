//
//  PotentialIncident.m
//  emission
//
//  Created by Felipe Arias
//
//

#import "PotentialIncident.h"
#import "DataUtils.h"
#import "SimpleLocation.h"
#import <CoreLocation/CLLocation.h>

//also need to take in mode of transportation
@implementation PotentialIncident
-(id) initWithLocationAccel:(SimpleLocation *)simpleLoc xVal:(double)xValue yVal:(double)yValue zVal:(double)zValue{
    self = [super init];
    self.latitude = simpleLoc.latitude;
    self.longitude = simpleLoc.longitude;
    self.altitude = simpleLoc.altitude;
    
    self.ts = simpleLoc.ts;
    self.fmt_time = simpleLoc.fmt_time;
    self.sensed_speed = simpleLoc.sensed_speed;
    self.accuracy = simpleLoc.accuracy;
    self.floor = simpleLoc.floor;
    self.bearing = simpleLoc.bearing;
    self.filter = @"distance";
    
    self.xValue = xValue;
    self.yValue = yValue;
    self.zValue = zValue;
    
    return self;
}

@end
