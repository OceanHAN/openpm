<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="520">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="阶段模板 + 工作量占比"
      description="禅道用「阶段模板」描述一套瀑布流程：需求/设计/开发/测试/发布……每个阶段带一个工作量占比，同一流程下累计不能超过 100%。项目创建时按模板生成自己的阶段。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="流程模板组" prop="workflowGroup">
        <el-input-number v-model="formData.workflowGroup" :min="1" :controls="false" class="w-full"
                         :disabled="formType === 'update'" />
        <div class="text-12px text-gray-400">同一个 workflowGroup 下的阶段属于同一套流程</div>
      </el-form-item>
      <el-form-item label="阶段名称" prop="name">
        <el-input v-model="formData.name" placeholder="如 开发" maxlength="255" />
      </el-form-item>
      <el-form-item label="阶段类型" prop="type">
        <el-select v-model="formData.type" placeholder="请选择类型" class="w-full">
          <el-option v-for="o in STAGE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作量占比" prop="percent">
        <el-input v-model="formData.percent" placeholder="如 30">
          <template #append>%</template>
        </el-input>
        <div class="text-12px text-gray-400">
          当前该流程已占用 {{ totalPercent }}%，本次最多还能填 {{ Math.max(0, 100 - totalPercent) }}%
        </div>
      </el-form-item>
      <el-form-item label="项目流程类型" prop="projectType">
        <el-select v-model="formData.projectType" class="w-full">
          <el-option v-for="o in PROJECT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as StageApi from '@/api/zentao/stage'
import { STAGE_TYPE_OPTIONS, PROJECT_TYPE_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoStageForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const totalPercent = ref(0)

const formData = ref<StageApi.StageVO>({})

const formRules = reactive({
  workflowGroup: [{ required: true, message: '流程模板组不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '阶段名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '阶段类型不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number, workflowGroup?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建阶段' : '编辑阶段'
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      const data = await StageApi.getStage(id)
      formData.value = { ...data }
      totalPercent.value = await StageApi.getTotalPercent(data.workflowGroup!)
      // 编辑时把本条的占比从已占用里扣掉，方便填写
      totalPercent.value = Number(
        (totalPercent.value - Number(data.percent || 0)).toFixed(2)
      )
    } finally {
      formLoading.value = false
    }
  } else {
    formData.value.workflowGroup = workflowGroup
    totalPercent.value = workflowGroup ? await StageApi.getTotalPercent(workflowGroup) : 0
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await StageApi.createStage(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await StageApi.updateStage(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = { workflowGroup: 1, name: '', percent: '', type: 'dev', projectType: 'waterfall' }
  totalPercent.value = 0
  formRef.value?.resetFields()
}
</script>
