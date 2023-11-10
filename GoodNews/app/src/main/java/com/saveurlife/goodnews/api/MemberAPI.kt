package com.saveurlife.goodnews.api

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MemberAPI {

    // Retrofit 인스턴스 생성
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.saveurlife.kr/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()


    private val memberService = retrofit.create(MemberInterface::class.java)
    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    // 멤버 정보 수정
    fun updateMemberInfo(memberId : String, name:String, birthDate:String, bloodType:String, addInfo:String){
        // request
        val data = RequestMemberInfo(name, birthDate, bloodType, addInfo)
        val json = gson.toJson(data)
        val requestBody = json.toRequestBody(mediaType)

        val call = memberService.updateMemberInfo(memberId, requestBody)
        call.enqueue(object : Callback<ResponseModifyMember> {
            override fun onResponse(call: Call<ResponseModifyMember>, response: Response<ResponseModifyMember>) {
                if(response.isSuccessful){
                    val responseBody = response.body()

                    Log.d("API RESP", responseBody.toString())

                    // 받아온 데이터에 대한 응답을 처리
                    if(responseBody!=null){
                        val data = responseBody.data
                        // 원하는 작업을 여기에 추가해 주세요.







                    }else{
                        Log.d("API ERROR", "값이 안왔음.")
                    }
                } else {
                    Log.d("API ERROR", response.toString())
                }
            }
            override fun onFailure(call: Call<ResponseModifyMember>, t: Throwable) {
                Log.d("API ERROR", t.toString())
            }
        })
    }


    // 멤버 정보 조회
    fun findMemberInfo(memberId : String){

        // request
        val data = RequestMemberId(memberId)
        val json = gson.toJson(data)
        val requestBody = json.toRequestBody(mediaType)

        val call = memberService.findMemberInfo(requestBody)

        // response
        call.enqueue(object : Callback<ResponseMember> {
            override fun onResponse(call: Call<ResponseMember>, response: Response<ResponseMember>) {
                if(response.isSuccessful){
                    val responseBody = response.body()

                    Log.d("API RESP", responseBody.toString())

                    // 받아온 데이터에 대한 응답을 처리
                    if(responseBody!=null){
                        val data = responseBody.data
                        // 원하는 작업을 여기에 추가해 주세요.







                    }else{
                        Log.d("API ERROR", "값이 안왔음.")
                    }
                } else {
                    Log.d("API ERROR", response.toString())
                }
            }
            override fun onFailure(call: Call<ResponseMember>, t: Throwable) {
                Log.d("API ERROR", t.toString())
            }
        })
    }

    // 추가 정보 등록
    fun registMemberInfo(memberId:String, name:String, birthDate:String, gender:String, bloodType:String, addInfo:String){
        // request
        val data = RequestMemberAddInfo(memberId, name, birthDate, gender, bloodType, addInfo)
        val json = gson.toJson(data)
        val requestBody = json.toRequestBody(mediaType)

        val call = memberService.registMemberInfo(requestBody)

        // response
        call.enqueue(object : Callback<ResponseRegistMember> {
            override fun onResponse(call: Call<ResponseRegistMember>, response: Response<ResponseRegistMember>) {
                if(response.isSuccessful){
                    val responseBody = response.body()

                    Log.d("API RESP", responseBody.toString())

                    // 받아온 데이터에 대한 응답을 처리
                    if(responseBody!=null){
                        val data = responseBody.data
                        // 원하는 작업을 여기에 추가해 주세요.







                    }else{
                        Log.d("API ERROR", "값이 안왔음.")
                    }
                } else {
                    Log.d("API ERROR", response.toString())
                }
            }
            override fun onFailure(call: Call<ResponseRegistMember>, t: Throwable) {
                Log.d("API ERROR", t.toString())
            }
        })


    }

    // 최초 로그인 유무 조회
    fun firstLoginInfo(memberId:String){
        val data = RequestMemberId(memberId)
        val json = gson.toJson(data)
        val requestBody = json.toRequestBody(mediaType)

        val call = memberService.firstLoginInfo(requestBody)

        // response
        call.enqueue(object : Callback<ResponseLogin> {
            override fun onResponse(call: Call<ResponseLogin>, response: Response<ResponseLogin>) {
                if(response.isSuccessful){
                    val responseBody = response.body()

                    Log.d("API RESP", responseBody.toString())

                    // 받아온 데이터에 대한 응답을 처리
                    if(responseBody!=null){
                        val data = responseBody.data
                        // 원하는 작업을 여기에 추가해 주세요.







                    }else{
                        Log.d("API ERROR", "값이 안왔음.")
                    }
                } else {
                    Log.d("API ERROR", response.toString())
                }
            }
            override fun onFailure(call: Call<ResponseLogin>, t: Throwable) {
                Log.d("API ERROR", t.toString())
            }
        })


    }

}