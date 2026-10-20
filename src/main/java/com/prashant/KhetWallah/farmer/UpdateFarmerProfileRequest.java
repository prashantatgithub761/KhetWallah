package com.prashant.KhetWallah.farmer;
import jakarta.validation.constraints.*;
public record UpdateFarmerProfileRequest(@Size(max=100) String farmName,@Size(max=1000) String bio,@Size(max=100) String district,@Pattern(regexp="^$|^[1-9][0-9]{5}$",message="Enter a valid six-digit Indian PIN code") String pinCode){}
