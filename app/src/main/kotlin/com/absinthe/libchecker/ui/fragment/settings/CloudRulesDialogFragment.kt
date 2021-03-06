package com.absinthe.libchecker.ui.fragment.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.absinthe.libchecker.LibCheckerApp
import com.absinthe.libchecker.R
import com.absinthe.libchecker.api.ApiManager
import com.absinthe.libchecker.api.bean.CloudRuleInfo
import com.absinthe.libchecker.api.request.CloudRuleBundleRequest
import com.absinthe.libchecker.constant.GlobalValues
import com.absinthe.libchecker.database.entity.RuleEntity
import com.absinthe.libchecker.databinding.LayoutCloudRuleDialogBinding
import com.absinthe.libchecker.extensions.logd
import com.absinthe.libchecker.protocol.CloudRulesBundle
import com.absinthe.libchecker.ui.fragment.BaseBottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CloudRulesDialogFragment : BaseBottomSheetDialogFragment<LayoutCloudRuleDialogBinding>() {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiManager.root)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val request: CloudRuleBundleRequest = retrofit.create(CloudRuleBundleRequest::class.java)
    private var bundlesCount: Int = 1

    override fun initBinding(): LayoutCloudRuleDialogBinding = LayoutCloudRuleDialogBinding.inflate(layoutInflater)

    override fun init() {
        binding.header.tvTitle.text = getString(R.string.cloud_rules)
        binding.vfContainer.apply {
            setInAnimation(activity, R.anim.anim_fade_in)
            setOutAnimation(activity, R.anim.anim_fade_out)
        }
        binding.btnUpdate.setOnClickListener {
            for (i in 1..bundlesCount) {
                requestBundle(i)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
            request.requestCloudRuleInfo().enqueue(object : Callback<CloudRuleInfo> {
                override fun onResponse(call: Call<CloudRuleInfo>, response: Response<CloudRuleInfo>) {
                    val body = response.body()
                    body?.let {
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.tvLocalRulesVersion.text = GlobalValues.localRulesVersion.toString()
                            binding.tvRemoteRulesVersion.text = it.version.toString()
                            if (GlobalValues.localRulesVersion < it.version) {
                                binding.btnUpdate.isEnabled = true
                            }
                            binding.vfContainer.displayedChild = 1
                            bundlesCount = it.bundles
                        }
                    }
                }

                override fun onFailure(call: Call<CloudRuleInfo>, t: Throwable) {
                    Log.e("CloudRulesDialog", t.toString())
                }

            })
        }
    }

    private fun requestBundle(bundleCount: Int) {
        request.requestRulesBundle(bundleCount).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                response.body()?.let { rb ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val builder = CloudRulesBundle.parseFrom(rb.byteStream())
                        logd("CloudRulesDialog", "version = ${builder.version}")
                        logd("CloudRulesDialog", "count = ${builder.count}")
                        val rulesList = mutableListOf<RuleEntity>()
                        builder.rulesList.cloudRulesList.forEach { rule ->
                            rule?.let {
                                rulesList.add(RuleEntity(it.name, it.label, it.type, it.iconIndex, it.isRegexRule, it.regexName))
                            }
                        }
                        LibCheckerApp.repository.insertRules(rulesList)
                        withContext(Dispatchers.Main) {
                            binding.tvLocalRulesVersion.text = builder.version.toString()
                            binding.btnUpdate.isEnabled = false
                            GlobalValues.localRulesVersion = builder.version
                        }
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("CloudRulesDialog", t.toString())
            }

        })
    }
}