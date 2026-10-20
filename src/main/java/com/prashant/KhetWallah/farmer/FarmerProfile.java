package com.prashant.KhetWallah.farmer;
import jakarta.persistence.*;
@Entity @Table(name="farmer_profiles")
public class FarmerProfile {
 @Id @Column(name="user_id") private Long userId;
 @Column(name="farm_name",length=100) private String farmName;
 @Column(length=1000) private String bio;
 @Column(length=100) private String district;
 @Column(name="pin_code",length=6) private String pinCode;
 protected FarmerProfile(){}
 public FarmerProfile(Long userId){this.userId=userId;}
 public Long getUserId(){return userId;} public String getFarmName(){return farmName;} public String getBio(){return bio;} public String getDistrict(){return district;} public String getPinCode(){return pinCode;}
 public void update(String farmName,String bio,String district,String pinCode){this.farmName=clean(farmName);this.bio=clean(bio);this.district=clean(district);this.pinCode=clean(pinCode);}
 private static String clean(String value){return value==null||value.isBlank()?null:value.strip();}
}
