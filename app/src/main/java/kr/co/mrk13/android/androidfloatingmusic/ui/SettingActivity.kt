package kr.co.mrk13.android.androidfloatingmusic.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import kr.co.mrk13.android.androidfloatingmusic.R
import kr.co.mrk13.android.androidfloatingmusic.databinding.ActivitySettingBinding


/**
 * @author ross.
 */
class SettingActivity : AppCompatActivity() {

    private var mBinding: ActivitySettingBinding? = null
    private val binding get() = mBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = SettingFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(binding.settingLayout.id, fragment)
            .commit()
    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }
}

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}