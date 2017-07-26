//
//  PotentialIncident.h
//  emission
//
//  Created by Felipe Arias
//
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CLLocation.h>
#import "SimpleLocation.h"

@interface PotentialIncident : NSObject

- (instancetype)initWithLocationAccel:(SimpleLocation *)simpleLoc xVal:(double)x yVal:(double)y zVal:(double)z;

@property double latitude;
@property double longitude;
@property double altitude;

@property double xValue;
@property double yValue;
@property double zValue;

@property double ts;
@property NSString* fmt_time;

@property double sensed_speed;
@property double accuracy;
@property double bearing;

@property NSInteger floor;
@property NSString* filter;

@end