<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-row>
        <el-col :span="14">
          <el-form-item label="项目名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入项目名称" maxlength="90" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="项目代号" prop="code">
            <el-input v-model="formData.code" placeholder="如 ZENTAO-P1" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="管理模型" prop="model">
            <el-select v-model="formData.model" class="w-full">
              <el-option v-for="o in PROJECT_MODEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="formData.pri" class="w-full">
              <el-option v-for="o in PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="计划开始" prop="begin">
            <el-date-picker v-model="formData.begin" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束" prop="end">
            <el-date-picker v-model="formData.end" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="预计工时" prop="estimate">
            <el-input-number v-model="formData.estimate" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预算" prop="budget">
            <el-input-number v-model="formData.budget" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="6">
          <el-form-item label="产品负责人" prop="PO">
            <el-input v-model="formData.PO" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="项目经理" prop="PM">
            <el-input v-model="formData.PM" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="测试负责人" prop="QD">
            <el-input v-model="formData.QD" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="研发负责人" prop="RD">
            <el-input v-model="formData.RD" placeholder="账号" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="团队成员" prop="team">
        <el-input v-model="formData.team" placeholder="多个账号用逗号分隔，如 admin,dev1" />
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="关联产品" prop="hasProduct">
            <el-switch v-model="formData.hasProduct" :active-value="1" :inactive-value="0" />
            <span class="ml-8px text-gray-500 text-12px">为 0 时关闭项目会连带关闭自动创建的产品</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="多执行" prop="multiple">
            <el-switch v-model="formData.multiple" :active-value="1" :inactive-value="0" />
            <span class="ml-8px text-gray-500 text-12px">为 0 时关闭项目会连带关闭其执行</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="所属项目集" prop="parent">
        <el-select v-model="formData.parent" filterable clearable placeholder="不选 = 不属于任何项目集"
                   class="!w-320px">
          <el-option label="（不属于项目集）" :value="0" />
          <el-option v-for="p in programList" :key="p.id" :label="p.name!" :value="p.id!" />
        </el-select>
        <span class="ml-10px text-gray-500 text-12px">项目集、项目、执行共用 zt_project，项目靠 parent 归属</span>
      </el-form-item>

      <el-form-item label="项目描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="4" placeholder="请输入项目描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ProjectApi from '@/api/zentao/project'
import * as ProgramApi from '@/api/zentao/program'
import { PROJECT_MODEL_OPTIONS, PRI_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoProjectForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()

const formData = ref<ProjectApi.ProjectVO>({})

// 项目集下拉（下拉里只列项目集，不列项目/执行）
const programList = ref<ProgramApi.ProgramVO[]>([])

const formRules = reactive({
  name: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
  model: [{ required: true, message: '管理模型不能为空', trigger: 'change' }],
  pri: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增项目' : '编辑项目'
  formType.value = type
  resetForm()
  try {
    programList.value = await ProgramApi.getProgramSimpleList()
  } catch {
    programList.value = []
  }
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ProjectApi.getProject(id)
    } finally {
      formLoading.value = false
    }
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
      await ProjectApi.createProject(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await ProjectApi.updateProject(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    name: '',
    code: '',
    parent: 0,
    model: 'scrum',
    pri: 3,
    hasProduct: 1,
    multiple: 0,
    budget: 0,
    estimate: 0,
    PO: 'admin',
    PM: 'admin',
    QD: '',
    RD: '',
    team: 'admin',
    desc: ''
  }
  formRef.value?.resetFields()
}
</script>
